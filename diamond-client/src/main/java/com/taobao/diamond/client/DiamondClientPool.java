/**
 * 
 */
package com.taobao.diamond.client;

import java.util.concurrent.ConcurrentHashMap;

import com.taobao.diamond.common.Constants;
import com.taobao.diamond.manager.DiamondManager;
import com.taobao.diamond.manager.ManagerListener;

/**
 * <pre>
 *
 * </pre>
 *
 * @author Wayne.Wang<5waynewang@gmail.com>
 * @since 6:06:26 PM Jan 30, 2016
 */
public class DiamondClientPool {

	private static final ConcurrentHashMap<String, DiamondManager> cache = new ConcurrentHashMap<String, DiamondManager>();

	public static String getConfigure(String dataId) {

		return getConfigure(dataId, Constants.DEFAULT_GROUP, (ManagerListener[]) null);
	}

	public static String getConfigure(String dataId, long timeout) {

		return getConfigure(dataId, Constants.DEFAULT_GROUP, timeout, (ManagerListener[]) null);
	}

	public static String getConfigure(String dataId, ManagerListener... managerListeners) {

		return getConfigure(dataId, Constants.DEFAULT_GROUP, managerListeners);
	}

	public static String getConfigure(String dataId, long timeout, ManagerListener... managerListeners) {

		return getConfigure(dataId, Constants.DEFAULT_GROUP, timeout, managerListeners);
	}

	public static String getConfigure(String dataId, String group, ManagerListener... managerListeners) {
		return getConfigure(dataId, Constants.DEFAULT_GROUP, 0, managerListeners);
	}

	public static String getConfigure(String dataId, String group, long timeout, ManagerListener... managerListeners) {
		final String managerKey = managerKey(dataId, group);
		DiamondManager diamondManager = cache.get(managerKey);

		if (diamondManager == null) {

			diamondManager = DiamondClients.createSafeDiamondManager(group, dataId, managerListeners);

			final DiamondManager oldDiamondManager = cache.putIfAbsent(managerKey, diamondManager);

			if (oldDiamondManager != null) {
				diamondManager.close();//关闭刚创建的
				diamondManager = oldDiamondManager;
			}
		}
		return diamondManager.getAvailableConfigureInfomation(timeout);
	}

	private static String managerKey(String dataId, String group) {
		return dataId + "-" + group;
	}
}
