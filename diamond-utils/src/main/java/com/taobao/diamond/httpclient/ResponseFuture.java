/**
 * Copyright (C) 2010-2013 Alibaba Group Holding Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.taobao.diamond.httpclient;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.netty.handler.codec.http.HttpResponse;

/**
 * 异步请求应答封装
 * 
 * @author shijia.wxr<vintage.wang@gmail.com>
 * @since 2013-7-13
 */
public class ResponseFuture {
	private final HttpInvokeResult invokeResult = new HttpInvokeResult();
	private volatile boolean sendRequestOK = true;
	private final int opaque;
	private final long timeoutMillis;
	private final long beginTimestamp = System.currentTimeMillis();
	private final CountDownLatch countDownLatch = new CountDownLatch(1);

	public ResponseFuture(int opaque, long timeoutMillis) {
		this.opaque = opaque;
		this.timeoutMillis = timeoutMillis;
	}

	public boolean isTimeout() {
		long diff = System.currentTimeMillis() - this.beginTimestamp;
		return diff > this.timeoutMillis;
	}

	public HttpInvokeResult waitResponse(final long timeoutMillis) throws InterruptedException {
		this.countDownLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
		return this.invokeResult;
	}

	public void putResponse(final HttpResponse response) {
		this.invokeResult.setResponse(response);
		this.countDownLatch.countDown();
	}

	public long getBeginTimestamp() {
		return beginTimestamp;
	}

	public boolean isSendRequestOK() {
		return sendRequestOK;
	}

	public void setSendRequestOK(boolean sendRequestOK) {
		this.sendRequestOK = sendRequestOK;
	}

	public long getTimeoutMillis() {
		return timeoutMillis;
	}

	public void setCause(Throwable cause) {
		this.invokeResult.setCause(cause);
	}

	public int getOpaque() {
		return opaque;
	}

	@Override
	public String toString() {
		return "ResponseFuture ["//
				+ "invokeResult=" + invokeResult //
				+ ", sendRequestOK=" + sendRequestOK//
				+ ", opaque=" + opaque //
				+ ", timeoutMillis=" + timeoutMillis//
				+ ", beginTimestamp=" + beginTimestamp//
				+ ", countDownLatch=" + countDownLatch //
				+ "]";
	}
}
