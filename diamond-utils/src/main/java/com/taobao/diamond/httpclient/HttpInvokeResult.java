package com.taobao.diamond.httpclient;

import java.nio.charset.Charset;

import org.apache.commons.lang3.StringUtils;

import com.taobao.diamond.common.Constants;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * @author Wayne.Wang<5waynewang@gmail.com>
 * @since 8:05:22 PM Jul 28, 2014
 */
public class HttpInvokeResult {
	private String remoteAddress;
	private String requestURI;
	private String requestMethod;
	private String reason;

	private volatile Throwable cause;
	private volatile HttpHeaders headers;
	private volatile HttpResponseStatus status;
	private volatile ByteBuf content;

	public int getStatusCode() {
		return status == null ? 0 : status.code();
	}

	public byte[] getResponseBody() {
		if (content == null) {
			return null;
		}
		return content.array();
	}

	/**
	 * <pre>
	 * convert response bytes to String
	 * </pre>
	 * 
	 * @return
	 */
	public String getResponseBodyAsString() {
		if (content == null) {
			return null;
		}
		return content.toString(Charset.forName(Constants.ENCODE));
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public String getReason() {
		return StringUtils.defaultIfBlank(reason, status == null ? null : status.reasonPhrase());
	}

	public Throwable getCause() {
		return cause;
	}

	public void setCause(Throwable cause) {
		this.cause = cause;
	}

	public boolean isSuccess() {
		return getStatusCode() == 200;
	}

	public String getRemoteAddress() {
		return remoteAddress;
	}

	public void setRemoteAddress(String remoteAddress) {
		this.remoteAddress = remoteAddress;
	}

	public String getRequestURI() {
		return requestURI;
	}

	public void setRequestURI(String requestURI) {
		this.requestURI = requestURI;
	}

	public String getRequestMethod() {
		return requestMethod;
	}

	public void setRequestMethod(String requestMethod) {
		this.requestMethod = requestMethod;
	}

	@Override
	public String toString() {
		return "RemoteAddress:[" + getRemoteAddress() //
				+ "], RequestURI:[" + getRequestURI()//
				+ "], RequestMethod:[" + getRequestMethod() //
				+ (status == null ? "" : "], StatusCode:[" + getStatusCode())//
				+ ((status == null || isSuccess()) ? "" : "], Reason:[" + getReason())//
				+ (cause == null ? "" : "], Cause:[" + getCause()) //
				+ "]";
	}

	public boolean noResponse() {
		return this.status == null;
	}

	public void setResponse(HttpResponse response) {
		this.headers = response.headers();
		this.status = response.getStatus();
		if (response instanceof HttpContent) {
			final ByteBuf content = ((HttpContent) response).content();
			if (content != null) {
				this.content = Unpooled.copiedBuffer(content);
			}
		}
	}

	public String getHeader(final String name) {
		return headers == null ? null : headers.get(name);
	}
}
