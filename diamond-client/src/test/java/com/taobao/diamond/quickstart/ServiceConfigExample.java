/**
 * 
 */
package com.taobao.diamond.quickstart;

import java.io.StringReader;
import java.util.Properties;
import java.util.concurrent.Executor;

import com.taobao.diamond.client.DiamondClients;
import com.taobao.diamond.manager.DiamondManager;
import com.taobao.diamond.manager.ManagerListener;

/**
 * <pre>
 *
 * </pre>
 * 
 * @author Wayne.Wang<5waynewang@gmail.com>
 * @since 10:34:39 AM Jan 14, 2015
 */
// register spring bean
public class ServiceConfigExample {

	DiamondManager diamondManager;

	Properties properties;

	// initialized by spring
	public void afterPropertiesSet() {
		diamondManager = DiamondClients.createSafeDiamondManager("GROUP", "DATA_ID", new ManagerListener() {
			@Override
			public void receiveConfigInfo(String configInfo) {
				try {
					final Properties properties = new Properties();
					properties.load(new StringReader(configInfo));
					ServiceConfigExample.this.properties = properties;
				} catch (Throwable ignore) {
					// do something
				}
			}

			@Override
			public Executor getExecutor() {
				return null;
			}
		});

		properties = diamondManager.getAvailablePropertiesConfigureInfomation(5000);

		if (properties == null || properties.isEmpty()) {
			// do something
		}
	}

	// destroy by spring
	public void destroy() {
		diamondManager.close();
	}

	// get config1
	public String getConfig1() {
		return this.properties.getProperty("config1");
	}
}
