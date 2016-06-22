/**
 * 
 */
package com.taobao.diamond.httpclient;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.taobao.diamond.common.Constants;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;

/**
 * <pre>
 *
 * </pre>
 *
 * @author Wayne.Wang<5waynewang@gmail.com>
 * @since 4:36:56 PM Jun 20, 2016
 */
public class HttpClientTest {
	HttpClient httpClient;

	@Before
	public void before() {
		this.httpClient = new HttpClient();
		this.httpClient.start();
	}

	@After
	public void after() {
		this.httpClient.shutdown();
	}

	@Test
	public void testInvoke() throws Exception {
//		final String host = "config.ixiaopu.com";
		final String host = "dev.xiangqutest.com";
		final int port = 54321;

		// Prepare the HTTP request.
		HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
				"/config.co?group=XIANGQU&dataId=GLOBAL");
		request.headers().set(HttpHeaders.Names.HOST, host);
		request.headers().set(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.GZIP);

		final HttpInvokeResult result = this.httpClient.invokeSync(host + ":" + port, request, 5000);

		System.out.println(result);
	}

	@Test
	public void testPost() throws Exception {
//		final String host = "config.ixiaopu.com";
		final String host = "dev.xiangqutest.com";
		final int port = 54321;

		Collection<Pair<String, ?>> parameters = new ArrayList<>();
		parameters.add(Pair.of(Constants.PROBE_MODIFY_REQUEST, "MEMCACHEDXIANGQU3e433623c8761a1903e2882afd52377c"));

		final HttpInvokeResult result = this.httpClient.post(host + ":" + port, "/config.co", parameters,
				5000);

		System.out.println(result);
		
		final String content = result.getResponseBodyAsString();
		
		System.out.println(content);
	}
	
	@Test
	public void testGet() throws Exception {
//		final String host = "config.ixiaopu.com";
		final String host = "dev.xiangqutest.com";
		final int port = 54321;

		Collection<Pair<String, ?>> headers = new ArrayList<>();
		headers.add(Pair.of(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.GZIP));

		final HttpInvokeResult result = this.httpClient.get(host + ":" + port, "/config.co?group=XIANGQU&dataId=GLOBAL", headers,
				5000);

		System.out.println(result);
		
		final String content = result.getResponseBodyAsString();
		
		System.out.println(content);
		
		final byte[] data = result.getResponseBody();
		
		System.out.println(new String(data, Constants.ENCODE));
		
		System.out.println(result.getHeader(Constants.CONTENT_MD5));
		
		System.out.println();
	}
	
	@Test
	public void testGet2() throws Exception {
		System.out.println(this.httpClient.get("config.xiangqutest.com:12345", "/ServerNodes", 5000));
	}
}
