/**
 * 
 */
package com.taobao.diamond.httpclient;

import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.taobao.diamond.common.Constants;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder;
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder.ErrorDataEncoderException;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

/**
 * <pre>
 * </pre>
 *
 * @author Wayne.Wang<5waynewang@gmail.com>
 * @since 12:20:36 PM May 26, 2016
 */
public class HttpClient {
	private static final Log log = LogFactory.getLog(HttpClient.class);

	private static final long LOCK_TIMEOUT_MILLIS = 3000;
	private static AtomicInteger RequestId = new AtomicInteger(0);

	private final Lock lockChannelTables = new ReentrantLock();
	private final ConcurrentHashMap<String /* addr */, ChannelWrapper> channelTables = new ConcurrentHashMap<String, ChannelWrapper>();

	private final Bootstrap bootstrap = new Bootstrap();
	private final EventLoopGroup eventLoopGroupWorker;
	private EventLoopGroup defaultEventExecutorGroup;

	// 缓存所有对外请求
	protected final ConcurrentHashMap<Integer /* opaque */, ResponseFuture> responseTable = new ConcurrentHashMap<Integer, ResponseFuture>(
			256);

	class ChannelWrapper {
		private final ChannelFuture channelFuture;

		public ChannelWrapper(ChannelFuture channelFuture) {
			this.channelFuture = channelFuture;
		}

		public boolean isOK() {
			return (this.channelFuture.channel() != null && this.channelFuture.channel().isActive());
		}

		public boolean isWriteable() {
			return this.channelFuture.channel().isWritable();
		}

		private Channel getChannel() {
			return this.channelFuture.channel();
		}

		public ChannelFuture getChannelFuture() {
			return channelFuture;
		}
	}

	class NettyClientHandler extends SimpleChannelInboundHandler<HttpObject> {

		@Override
		protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
			processResponseMsg(ctx, (HttpResponse) msg);
		}
	}

	class NettyConnetManageHandler extends ChannelDuplexHandler {
		@Override
		public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress,
				ChannelPromise promise) throws Exception {
			final String local = localAddress == null ? "UNKNOW" : localAddress.toString();
			final String remote = remoteAddress == null ? "UNKNOW" : remoteAddress.toString();
			log.info("NETTY CLIENT PIPELINE: CONNECT  " + local + " => " + remote);
			super.connect(ctx, remoteAddress, localAddress, promise);
		}

		@Override
		public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
			final String remoteAddress = parseChannelRemoteAddr(ctx.channel());
			log.info("NETTY CLIENT PIPELINE: DISCONNECT " + remoteAddress);
			closeChannel(ctx.channel());
			super.disconnect(ctx, promise);
		}

		@Override
		public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
			final String remoteAddress = parseChannelRemoteAddr(ctx.channel());
			log.info("NETTY CLIENT PIPELINE: CLOSE " + remoteAddress);
			closeChannel(ctx.channel());
			super.close(ctx, promise);
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
			final String remoteAddress = parseChannelRemoteAddr(ctx.channel());
			log.warn("NETTY CLIENT PIPELINE: exceptionCaught " + remoteAddress);
			log.warn("NETTY CLIENT PIPELINE: exceptionCaught exception.", cause);
			closeChannel(ctx.channel());
		}

		@Override
		public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
			if (evt instanceof IdleStateEvent) {
				IdleStateEvent evnet = (IdleStateEvent) evt;
				if (evnet.state().equals(IdleState.ALL_IDLE)) {
					final String remoteAddress = parseChannelRemoteAddr(ctx.channel());
					log.warn("NETTY CLIENT PIPELINE: IDLE exception [" + remoteAddress + "]");
					closeChannel(ctx.channel());
				}
			}

			ctx.fireUserEventTriggered(evt);
		}
	}

	public HttpClient() {
		this.eventLoopGroupWorker = new NioEventLoopGroup(1, new ThreadFactory() {
			private AtomicInteger threadIndex = new AtomicInteger(0);

			@Override
			public Thread newThread(Runnable r) {
				return new Thread(r, String.format("NettyHttpClientSelector_%d", this.threadIndex.incrementAndGet()));
			}
		});
	}

	public void start() {
		this.defaultEventExecutorGroup = new NioEventLoopGroup();

		this.bootstrap.group(this.eventLoopGroupWorker)//
				.channel(NioSocketChannel.class)//
				.option(ChannelOption.TCP_NODELAY, true)//
				.option(ChannelOption.SO_KEEPALIVE, false)//
				.option(ChannelOption.SO_SNDBUF, 65535)//
				.option(ChannelOption.SO_RCVBUF, 65535)//
				.handler(new ChannelInitializer<SocketChannel>() {
					@Override
					protected void initChannel(SocketChannel ch) throws Exception {
						ch.pipeline().addLast(//
								defaultEventExecutorGroup, //
								new HttpClientCodec(), //
								// Remove the following line if you don't want automatic content decompression.
								new HttpContentDecompressor(), //
								// 由于HTTP消息可能分散为多个消息，处理它们比较繁琐，Netty提供了聚合工具，可以将HttpObject聚合为完整的FullHttpRequest、FullHttpResponse消息
								// 限制消息的最大长度为1MB，如果超过会导致TooLongFrameException
								new HttpObjectAggregator(Constants.MAX_CONTENT_LENGTH), //
								// 在空闲120秒后，此处理器被激，触发，调用下一个处理器的userEventTriggered
								// IdleStateHandler构造器的前三个参数：读空闲时间、写空闲时间、都空闲时间
								new IdleStateHandler(0, 0, Constants.CLIENT_CHANNEL_MAX_IDLE_TIME_SECONDS), //
								new NettyConnetManageHandler(), //
								new NettyClientHandler());
					}

				});
	}

	public void shutdown() {
		try {
			for (ChannelWrapper cw : this.channelTables.values()) {
				this.closeChannel(null, cw.getChannel());
			}

			this.channelTables.clear();

			this.eventLoopGroupWorker.shutdownGracefully();

			if (this.defaultEventExecutorGroup != null) {
				this.defaultEventExecutorGroup.shutdownGracefully();
			}
		}
		catch (Exception e) {
			log.error("NettyHttpClient shutdown exception, ", e);
		}
	}

	private Channel getAndCreateChannel(final String addr) throws InterruptedException {
		ChannelWrapper cw = this.channelTables.get(addr);
		if (cw != null && cw.isOK()) {
			return cw.getChannel();
		}

		return this.createChannel(addr);
	}

	/**
	 * IP:PORT
	 */
	public static SocketAddress string2SocketAddress(final String addr) {
		String[] s = addr.split(":");
		InetSocketAddress isa = new InetSocketAddress(s[0],
				s.length == 1 ? Constants.DEFAULT_PORT : Integer.valueOf(s[1]));
		return isa;
	}

	public static String parseChannelRemoteAddr(final Channel channel) {
		if (null == channel) {
			return "";
		}
		final SocketAddress remote = channel.remoteAddress();
		final String addr = remote != null ? remote.toString() : "";

		if (addr.length() > 0) {
			int index = addr.lastIndexOf("/");
			if (index >= 0) {
				return addr.substring(index + 1);
			}

			return addr;
		}

		return "";
	}

	public static void closeChannel(Channel channel) {
		final String addrRemote = parseChannelRemoteAddr(channel);
		if (StringUtils.isBlank(addrRemote)) {
			return;
		}

		channel.close().addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				log.info("closeChannel: close the connection to remote address[" + addrRemote + "] result: "
						+ future.isSuccess());
			}
		});
	}

	private Channel createChannel(final String addr) throws InterruptedException {
		ChannelWrapper cw = this.channelTables.get(addr);
		if (cw != null && cw.isOK()) {
			return cw.getChannel();
		}

		// 进入临界区后，不能有阻塞操作，网络连接采用异步方式
		if (this.lockChannelTables.tryLock(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
			try {
				boolean createNewConnection = false;
				cw = this.channelTables.get(addr);
				if (cw != null) {
					// channel正常
					if (cw.isOK()) {
						return cw.getChannel();
					}
					// 正在连接，退出锁等待
					else if (!cw.getChannelFuture().isDone()) {
						createNewConnection = false;
					}
					// 说明连接不成功
					else {
						this.channelTables.remove(addr);
						createNewConnection = true;
					}
				}
				// ChannelWrapper不存在
				else {
					createNewConnection = true;
				}

				if (createNewConnection) {
					ChannelFuture channelFuture = this.bootstrap.connect(string2SocketAddress(addr));
					log.info("createChannel: begin to connect remote host[" + addr + "] asynchronously");
					cw = new ChannelWrapper(channelFuture);
					this.channelTables.put(addr, cw);
				}
			}
			catch (Exception e) {
				log.error("createChannel: create channel exception", e);
			}
			finally {
				this.lockChannelTables.unlock();
			}
		}
		else {
			log.warn("createChannel: try to lock channel table, but timeout, " + LOCK_TIMEOUT_MILLIS + "ms");
		}

		if (cw != null) {
			ChannelFuture channelFuture = cw.getChannelFuture();
			if (channelFuture.awaitUninterruptibly(Constants.CONN_TIMEOUT)) {
				if (cw.isOK()) {
					log.info("createChannel: connect remote host[" + addr + "] success, " + channelFuture.toString());
					return cw.getChannel();
				}
				else {
					log.warn("createChannel: connect remote host[" + addr + "] failed, " + channelFuture.toString(),
							channelFuture.cause());
				}
			}
			else {
				log.warn("createChannel: connect remote host[" + addr + "] timeout " + Constants.CONN_TIMEOUT + "ms, "
						+ channelFuture.toString());
			}
		}

		return null;
	}

	public void closeChannel(final String addr, final Channel channel) {
		if (null == channel) {
			return;
		}

		final String addrRemote = null == addr ? parseChannelRemoteAddr(channel) : addr;
		if (StringUtils.isBlank(addrRemote)) {
			return;
		}

		try {
			if (this.lockChannelTables.tryLock(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
				try {
					boolean removeItemFromTable = true;
					final ChannelWrapper prevCW = this.channelTables.get(addrRemote);

					log.info("closeChannel: begin close the channel[" + addrRemote + "] Found: " + (prevCW != null));

					if (null == prevCW) {
						log.info("closeChannel: the channel[" + addrRemote
								+ "] has been removed from the channel table before");
						removeItemFromTable = false;
					}
					else if (prevCW.getChannel() != channel) {
						log.info("closeChannel: the channel[" + addrRemote
								+ "] has been closed before, and has been created again, nothing to do.");
						removeItemFromTable = false;
					}

					if (removeItemFromTable) {
						this.channelTables.remove(addrRemote);
						log.info("closeChannel: the channel[" + addrRemote + "] was removed from channel table");
					}

					closeChannel(channel);
				}
				catch (Exception e) {
					log.error("closeChannel: close the channel exception", e);
				}
				finally {
					this.lockChannelTables.unlock();
				}
			}
			else {
				log.warn("closeChannel: try to lock channel table, but timeout, " + LOCK_TIMEOUT_MILLIS + "ms");
			}
		}
		catch (InterruptedException e) {
			log.error("closeChannel exception", e);
		}
	}

	public void processResponseMsg(ChannelHandlerContext ctx, HttpResponse msg) {
		final String value = HttpHeaders.getHeader(msg, Constants.OPAQUE);
		if (value == null) {
			log.warn("receive response, but not found opaque, " + parseChannelRemoteAddr(ctx.channel()));
			log.warn(msg);
			return;
		}

		final int opaque = Integer.parseInt(value);
		final ResponseFuture responseFuture = responseTable.get(opaque);
		if (responseFuture != null) {
			responseFuture.putResponse(msg);
		}
		else {
			log.warn("receive response, but not matched any request, " + parseChannelRemoteAddr(ctx.channel()));
			log.warn(msg);
		}

		responseTable.remove(opaque);
	}

	HttpRequest setHeaders(HttpRequest request, Collection<Pair<String, ?>> headers) {
		if (headers != null && !headers.isEmpty()) {
			for (Pair<String, ?> header : headers) {
				request.headers().add(header.getLeft(), header.getRight());
			}
		}
		return request;
	}

	public HttpInvokeResult get(String addr, String uri, long timeoutMillis) {
		return get(addr, uri, null, timeoutMillis);
	}

	public HttpInvokeResult get(String addr, String uri, Collection<Pair<String, ?>> headers, long timeoutMillis) {
		final HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
		request.headers().add(HttpHeaders.Names.HOST, addr);
		try {
			return invokeSync(addr, setHeaders(request, headers), timeoutMillis);
		}
		catch (InterruptedException e) {
			final HttpInvokeResult invokeResult = new HttpInvokeResult();
			invokeResult.setRemoteAddress(addr);
			invokeResult.setRequestURI(request.getUri());
			invokeResult.setRequestMethod(request.getMethod().name());
			invokeResult.setCause(e);
			return invokeResult;
		}
	}

	@SuppressWarnings("deprecation")
	String encodeFormFields(Object field) {
		if (field == null) {
			return null;
		}
		try {
			return URLEncoder.encode(ObjectUtils.toString(field), Constants.ENCODE);
		}
		catch (UnsupportedEncodingException e) {
			return URLEncoder.encode(ObjectUtils.toString(field));
		}
	}

	public HttpInvokeResult post(String addr, String uri, Collection<Pair<String, ?>> parameters, long timeoutMillis) {
		return post(addr, uri, null, parameters, timeoutMillis);
	}

	public HttpInvokeResult post(String addr, String uri, Collection<Pair<String, ?>> headers,
			Collection<Pair<String, ?>> parameters, long timeoutMillis) {
		HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri);

		request.headers().add(HttpHeaders.Names.HOST, addr);
		setHeaders(request, headers);

		if (parameters != null && !parameters.isEmpty()) {
			try {
				final HttpPostRequestEncoder bodyRequestEncoder = new HttpPostRequestEncoder(request, false);
				for (final Pair<String, ?> parameter : parameters) {
					bodyRequestEncoder.addBodyAttribute(parameter.getLeft(),
							ObjectUtils.toString(parameter.getRight(), null));
					//				final String encodedName = encodeFormFields(parameter.getLeft());
					//				final String encodedValue = encodeFormFields(parameter.getValue());
					//				if (result.length() > 0) {
					//					result.append('&');
					//				}
					//				result.append(encodedName);
					//				if (encodedValue != null) {
					//					result.append('=');
					//					result.append(encodedValue);
					//				}
				}

				request = bodyRequestEncoder.finalizeRequest();
			}
			catch (ErrorDataEncoderException e) {
				final HttpInvokeResult invokeResult = new HttpInvokeResult();
				invokeResult.setRemoteAddress(addr);
				invokeResult.setRequestURI(request.getUri());
				invokeResult.setRequestMethod(request.getMethod().name());
				invokeResult.setCause(e);
				return invokeResult;
			}
		}

		try {
			return invokeSync(addr, request, timeoutMillis);
		}
		catch (InterruptedException e) {
			final HttpInvokeResult invokeResult = new HttpInvokeResult();
			invokeResult.setRemoteAddress(addr);
			invokeResult.setRequestURI(request.getUri());
			invokeResult.setRequestMethod(request.getMethod().name());
			invokeResult.setCause(e);
			return invokeResult;
		}
	}

	public HttpInvokeResult invokeSync(final String addr, final HttpRequest request, final long timeoutMillis)
			throws InterruptedException {
		final Channel channel = this.getAndCreateChannel(addr);
		if (channel != null && channel.isActive()) {
			return this.invokeSyncImpl(channel, request, timeoutMillis);
		}
		else {
			final HttpInvokeResult invokeResult = new HttpInvokeResult();
			invokeResult.setRemoteAddress(addr);
			invokeResult.setRequestURI(request.getUri());
			invokeResult.setRequestMethod(request.getMethod().name());
			invokeResult.setCause(new RemotingConnectException(addr));

			log.warn("invokeSync: connect exception, " + invokeResult + ", close the channel");

			this.closeChannel(addr, channel);

			return invokeResult;
		}
	}

	public HttpInvokeResult invokeSyncImpl(final Channel channel, final HttpRequest request, final long timeoutMillis)
			throws InterruptedException {
		final int opaque = RequestId.getAndIncrement();
		try {
			final ResponseFuture responseFuture = new ResponseFuture(opaque, timeoutMillis);
			this.responseTable.put(opaque, responseFuture);

			// 通过 opaque绑定请求响应
			request.headers().add(Constants.OPAQUE, opaque);

			if (log.isDebugEnabled()) {
				log.debug(request);
			}

			channel.writeAndFlush(request).addListener(new ChannelFutureListener() {
				@Override
				public void operationComplete(ChannelFuture f) throws Exception {
					if (f.isSuccess()) {
						responseFuture.setSendRequestOK(true);
						return;
					}
					else {
						responseFuture.setSendRequestOK(false);
					}

					responseTable.remove(opaque);
					responseFuture.setCause(f.cause());
					responseFuture.putResponse(null);
					log.warn("send a request command to channel <" + channel.remoteAddress() + "> failed.");
					log.warn(request.toString());
				}
			});

			final String addrRemote = parseChannelRemoteAddr(channel);
			final HttpInvokeResult invokeResult = responseFuture.waitResponse(timeoutMillis);
			invokeResult.setRemoteAddress(addrRemote);
			invokeResult.setRequestURI(request.getUri());
			invokeResult.setRequestMethod(request.getMethod().name());
			if (invokeResult.noResponse()) {
				if (responseFuture.isSendRequestOK()) {

					log.warn("invokeSync: wait response timeout exception, " + invokeResult);

					invokeResult
							.setCause(new RemotingTimeoutException(addrRemote, timeoutMillis, invokeResult.getCause()));
				}
				else {
					log.warn("invokeSync: send request exception, " + invokeResult + ", close the channel");

					this.closeChannel(addrRemote, channel);

					invokeResult.setCause(new RemotingSendRequestException(addrRemote, invokeResult.getCause()));
				}
			}

			return invokeResult;
		}
		finally {
			this.responseTable.remove(opaque);
		}
	}

}
