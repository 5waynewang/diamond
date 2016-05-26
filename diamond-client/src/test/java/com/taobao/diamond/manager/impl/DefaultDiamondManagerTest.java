/**
 * 
 */
package com.taobao.diamond.manager.impl;

import java.util.concurrent.CountDownLatch;

import org.junit.Before;
import org.junit.Test;

import com.taobao.diamond.client.DiamondClients;
import com.taobao.diamond.manager.DiamondManager;
import com.taobao.diamond.manager.ManagerListenerAdapter;

/**
 * <pre>
 *
 * </pre>
 * 
 * @author Wayne.Wang<5waynewang@gmail.com>
 * @since 12:55:02 PM Jan 7, 2015
 */
public class DefaultDiamondManagerTest {
	DiamondManager ddm;

	@Before
	public void before() {
		this.ddm = DiamondClients.createSafeDiamondManager("XIANGQU", "GLOBAL", new ManagerListenerAdapter() {
			@Override
			public void receiveConfigInfo(String configInfo) {
				System.out.println(System.currentTimeMillis() + " >>> " + configInfo);
			}
		});
	}

	@Test
	public void testGetAvailableConfigureInfomation() throws Exception {
		final String configInfo = this.ddm.getAvailableConfigureInfomation();
		System.out.println(System.currentTimeMillis() + " >>> " + configInfo);

		new CountDownLatch(1).await();
	}
}
