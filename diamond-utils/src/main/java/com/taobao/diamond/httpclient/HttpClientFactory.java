/**
 * 
 */
package com.taobao.diamond.httpclient;

/**
 * <pre>
 *
 * </pre>
 *
 * @author Wayne.Wang<5waynewang@gmail.com>
 * @since 1:43:06 PM Jun 21, 2016
 */
public class HttpClientFactory {
	private static final HttpClient httpClient = new HttpClient();

	static {
		httpClient.start();

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				httpClient.shutdown();
			}
		});
	}

	public static HttpClient getHttpClient() {
		return httpClient;
	}
}
