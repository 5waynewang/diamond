Diamond是一个持久配置管理中心，核心功能是使应用在运行中感知配置数据的变化。


##pom依赖

<code>
<pre>
&lt;dependency&gt;
&lt;groupId&gt;com.taobao.diamond&lt;/groupId&gt;
&lt;artifactId&gt;diamond-client&lt;/artifactId&gt;
&lt;version&gt;3.0-SNAPSHOT&lt;/version&gt;
&lt;/dependency&gt;
</code>
</pre>

##建表语句
<pre>
CREATE TABLE `config_info` (
  `id` bigint(64) unsigned NOT NULL AUTO_INCREMENT,
  `data_id` varchar(128) NOT NULL COMMENT '配置项的key值',
  `group_id` varchar(128) NOT NULL COMMENT '配置项分组名称',
  `content` longtext NOT NULL COMMENT '配置项内容',
  `md5` varchar(32) NOT NULL COMMENT '内容的md5值',
  `gmt_create` timestamp NULL DEFAULT NULL COMMENT '更新时间',
  `gmt_modified` timestamp NULL DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_datagroup` (`data_id`,`group_id`) USING BTREE
) ENGINE=MyISAM AUTO_INCREMENT=3 DEFAULT CHARSET=utf8;
</pre>

##java代码调用

<code>
<pre>
DiamondManager diamondManager = DiamondClients.createSafeDiamondManager("MyGroup", "MyDataId", new ManagerListener() {
	@Override
	public void receiveConfigInfo(String configInfo) {
		// 配置变更异步通知
		try {
			final Properties properties = new Properties();
			properties.load(new StringReader(configInfo));
			
			// ... ...
			
		} catch (Throwable ignore) {
		}
	}
	@Override
	public Executor getExecutor() {
		return null;
	}
});
</pre>
</code>

<code>
<pre>
// 主动获取配置
Properties properties = diamondManager.getAvailablePropertiesConfigureInfomation();
</pre>
</code>

###管理后台

http://config.ixiaopu.com:54321/

admin / admin