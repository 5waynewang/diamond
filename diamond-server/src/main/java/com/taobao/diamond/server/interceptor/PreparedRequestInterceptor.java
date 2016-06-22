package com.taobao.diamond.server.interceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import com.taobao.diamond.common.Constants;

/**
 * <pre>
 *
 * </pre>
 *
 * @author Wayne.Wang<5waynewang@gmail.com>
 * @since 10:08:12 AM Jun 22, 2016
 */
public class PreparedRequestInterceptor extends HandlerInterceptorAdapter {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		final String opaque = request.getHeader(Constants.OPAQUE);
		if (opaque != null) {
			response.setHeader(Constants.OPAQUE, opaque);
		}

		return true;
	}
}
