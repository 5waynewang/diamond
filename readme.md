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

##java代码调用

<code>
<pre>
DiamondManager diamondManager = DiamondClients.createSafeDiamondManager("XIANGQU", "GLOBAL", new ManagerListener() {
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
// 获取配置
Properties properties = diamondManager.getAvailablePropertiesConfigureInfomation(5000);
</pre>
</code>

###管理后台

http://config.ixiaopu.com:54321/

admin / admin