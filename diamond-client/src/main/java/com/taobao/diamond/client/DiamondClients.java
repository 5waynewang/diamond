/**
 * 
 */
package com.taobao.diamond.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.taobao.diamond.client.impl.DiamondClientFactory;
import com.taobao.diamond.manager.DiamondManager;
import com.taobao.diamond.manager.ManagerListener;
import com.taobao.diamond.manager.impl.DefaultDiamondManager;

/**
 * <pre>
 *
 * </pre>
 * 
 * @author Wayne.Wang<5waynewang@gmail.com>
 * @since 4:22:32 PM Jan 7, 2015
 */
public class DiamondClients {

//	static final String DEFAULT_DOMAIN = "config.ixiaopu.com";
//	static {
//		final DiamondConfigure configure = DiamondClientFactory.getSingletonDiamondSubscriber().getDiamondConfigure();
//		configure.setConfigServerAddress(DEFAULT_DOMAIN);
//		configure.addDomainName(DEFAULT_DOMAIN);
//	}

	@SuppressWarnings("unchecked")
	static <T> List<T> toList(T... objs) {
		if (objs == null || objs.length == 0) {
			return Collections.EMPTY_LIST;
		} else {
			final List<T> results = new ArrayList<T>(objs.length);
			Collections.addAll(results, objs);
			return results;
		}
	}

	public static DiamondManager createSafeDiamondManager(String dataId, ManagerListener... managerListeners) {
		return new DefaultDiamondManager(dataId, toList(managerListeners));
	}

	public static DiamondManager createSafeDiamondManager(String group, String dataId,
			ManagerListener... managerListeners) {
		return new DefaultDiamondManager(group, dataId, toList(managerListeners));
	}

}
