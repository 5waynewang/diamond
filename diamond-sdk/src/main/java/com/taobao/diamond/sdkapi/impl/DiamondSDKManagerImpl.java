/*
 * (C) 2007-2012 Alibaba Group Holding Limited.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation.
 * Authors:
 *   leiwen <chrisredfield1985@126.com> , boyan <killme2008@gmail.com>
 */
package com.taobao.diamond.sdkapi.impl;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.alibaba.fastjson.TypeReference;
import com.taobao.diamond.common.Constants;
import com.taobao.diamond.domain.BatchContextResult;
import com.taobao.diamond.domain.ConfigInfo;
import com.taobao.diamond.domain.ConfigInfoEx;
import com.taobao.diamond.domain.ContextResult;
import com.taobao.diamond.domain.DiamondConf;
import com.taobao.diamond.domain.DiamondSDKConf;
import com.taobao.diamond.domain.Page;
import com.taobao.diamond.domain.PageContextResult;
import com.taobao.diamond.httpclient.HttpClientFactory;
import com.taobao.diamond.httpclient.HttpInvokeResult;
import com.taobao.diamond.sdkapi.DiamondSDKManager;
import com.taobao.diamond.util.PatternUtils;
import com.taobao.diamond.util.RandomDiamondUtils;
import com.taobao.diamond.utils.JSONUtils;

/**
 * SDK对外开放的数据接口的功能实现
 * 
 * @filename DiamondSDKManagerImpl.java
 * @author libinbin.pt
 * @datetime 2010-7-16 下午04:00:19
 */
public class DiamondSDKManagerImpl implements DiamondSDKManager {

    private static final Log log = LogFactory.getLog("diamondSdkLog");

    // DiamondSDKConf配置集map
    private Map<String, DiamondSDKConf> diamondSDKConfMaps;

    // 连接超时时间
    private final int connection_timeout;
    // 请求超时时间
    private final int require_timeout;

    // 最后一次设置的 DiamondConf
    private volatile DiamondConf lastDiamondConf;
    
    // 构造时需要传入连接超时时间，请求超时时间
    public DiamondSDKManagerImpl(int connection_timeout, int require_timeout) throws IllegalArgumentException {
        if (connection_timeout < 0)
            throw new IllegalArgumentException("连接超时时间设置必须大于0[单位(毫秒)]!");
        if (require_timeout < 0)
            throw new IllegalArgumentException("请求超时时间设置必须大于0[单位(毫秒)]!");
        this.connection_timeout = connection_timeout;
        this.require_timeout = require_timeout;
        
        log.info("设置连接超时时间为: " + this.connection_timeout + "毫秒");
    }

    
	private String getAddr() {
		return lastDiamondConf.getDiamondIp() + ":" + lastDiamondConf.getDiamondPort();
	}

    /**
     * 使用指定的diamond来推送数据
     * 
     * @param dataId
     * @param groupName
     * @param context
     * @param serverId
     * @return ContextResult 单个对象
     */
    public synchronized ContextResult pulish(String dataId, String groupName, String context, String serverId) {
        ContextResult response = null;
        // 进行dataId,groupName,context,serverId为空验证
        if (validate(dataId, groupName, context)) {
            response = this.processPulishByDefinedServerId(dataId, groupName, context, serverId);
            return response;
        }

        // 未通过为空验证
        response = new ContextResult();
        response.setSuccess(false);
        response.setStatusMsg("请确保dataId,group,content不为空");
        return response;
    }


    /**
     * 使用指定的diamond来推送修改后的数据
     * 
     * @param dataId
     * @param groupName
     * @param context
     * @param serverId
     * @return ContextResult 单个对象
     */
    public synchronized ContextResult pulishAfterModified(String dataId, String groupName, String context,
            String serverId) {

        ContextResult response = null;
        // 进行dataId,groupName,context,serverId为空验证
        if (validate(dataId, groupName, context)) {
            // 向diamondserver发布修改数据
            response = this.processPulishAfterModifiedByDefinedServerId(dataId, groupName, context, serverId);
            return response;
        }
        else {
            response = new ContextResult();
            // 未通过为空验证
            response.setSuccess(false);
            response.setStatusMsg("请确保dataId,group,content不为空");
            return response;
        }

    }


    // -------------------------模糊查询-------------------------------//
    /**
     * 使用指定的diamond来模糊查询数据
     * 
     * @param dataIdPattern
     * @param groupNamePattern
     * @param serverId
     * @param currentPage
     * @param sizeOfPerPage
     * @return PageContextResult<ConfigInfo> 单个对象
     * @throws SQLException
     */
    public synchronized PageContextResult<ConfigInfo> queryBy(String dataIdPattern, String groupNamePattern,
            String serverId, long currentPage, long sizeOfPerPage) {
        return processQuery(dataIdPattern, groupNamePattern, null, serverId, currentPage, sizeOfPerPage);
    }


    /**
     * 根据指定的 dataId,组名和content到指定配置的diamond来查询数据列表 如果模式中包含符号'*',则会自动替换为'%'并使用[
     * like ]语句 如果模式中不包含符号'*'并且不为空串（包括" "）,则使用[ = ]语句
     * 
     * @param dataIdPattern
     * @param groupNamePattern
     * @param contentPattern
     * @param serverId
     * @param currentPage
     * @param sizeOfPerPage
     * @return PageContextResult<ConfigInfo> 单个对象
     * @throws SQLException
     */

    public synchronized PageContextResult<ConfigInfo> queryBy(String dataIdPattern, String groupNamePattern,
            String contentPattern, String serverId, long currentPage, long sizeOfPerPage) {
        return processQuery(dataIdPattern, groupNamePattern, contentPattern, serverId, currentPage, sizeOfPerPage);
    }


    // =====================精确查询 ==================================

    /**
     * 使用指定的diamond和指定的dataId,groupName来精确查询数据
     * 
     * @param dataId
     * @param groupName
     * @param serverId
     * @return ContextResult 单个对象
     * @throws SQLException
     */
    public synchronized ContextResult queryByDataIdAndGroupName(String dataId, String groupName, String serverId) {
        ContextResult result = new ContextResult();
        PageContextResult<ConfigInfo> pageContextResult = processQuery(dataId, groupName, null, serverId, 1, 1);
        result.setStatusMsg(pageContextResult.getStatusMsg());
        result.setSuccess(pageContextResult.isSuccess());
        result.setStatusCode(pageContextResult.getStatusCode());
        if (pageContextResult.isSuccess()) {
            List<ConfigInfo> list = pageContextResult.getDiamondData();
            if (list != null && !list.isEmpty()) {
                ConfigInfo info = list.iterator().next();
                result.setConfigInfo(info);
                result.setReceiveResult(info.getContent());
                result.setStatusCode(pageContextResult.getStatusCode());

            }
        }
        return result;
    }

    // ========================精确查询结束==================================

    // /////////////////////////私有工具对象定义和工具方法实现////////////////////////////////////////


    // =========================== 推送 ===============================

    private ContextResult processPulishByDefinedServerId(String dataId, String groupName, String context,
            String serverId) {
        ContextResult response = new ContextResult();
        // 登录
        if (!login(serverId)) {
            response.setSuccess(false);
            response.setStatusMsg("登录失败,造成错误的原因可能是指定的serverId为空或不存在");
            return response;
        }
        if (log.isDebugEnabled())
            log.debug("使用processPulishByDefinedServerId(" + dataId + "," + groupName + "," + context + "," + serverId
                    + ")进行推送");

        // 设置参数
    	final Collection<Pair<String, ?>> parameters = new ArrayList<Pair<String, ?>>();
    	parameters.add(Pair.of("method", "postConfig"));
    	parameters.add(Pair.of("dataId", dataId));
    	parameters.add(Pair.of("group", groupName));
    	parameters.add(Pair.of("content", context));
        
        final HttpInvokeResult result = HttpClientFactory.getHttpClient().post(getAddr(), "/admin", parameters, require_timeout);

        // 配置对象
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setDataId(dataId);
        configInfo.setGroup(groupName);
        configInfo.setContent(context);
        if (log.isDebugEnabled())
            log.debug("待推送的ConfigInfo: " + configInfo);
        // 添加一个配置对象到响应结果中
        response.setConfigInfo(configInfo);
        // 执行方法并返回http状态码
        int status = result.getStatusCode();
        response.setReceiveResult(result.getResponseBodyAsString());
        response.setStatusCode(status);
        log.info("状态码：" + status + ",响应结果：" + response.getReceiveResult());
        if (status == Constants.SC_OK) {
            response.setSuccess(true);
            response.setStatusMsg("推送处理成功");
            log.info("推送处理成功, dataId=" + dataId + ",group=" + groupName + ",content=" + context + ",serverId="
                    + serverId);
        }
        else if (status == Constants.SC_REQUEST_TIMEOUT) {
            response.setSuccess(false);
            response.setStatusMsg("推送处理超时, 默认超时时间为:" + require_timeout + "毫秒");
            log.error("推送处理超时，默认超时时间为:" + require_timeout + "毫秒, dataId=" + dataId + ",group=" + groupName
                    + ",content=" + context + ",serverId=" + serverId);
        }
        else {
            response.setSuccess(false);
            response.setStatusMsg("推送处理失败, 状态码为:" + status);
            log.error("推送处理失败:" + response.getReceiveResult() + ",dataId=" + dataId + ",group=" + groupName
                    + ",content=" + context + ",serverId=" + serverId);
        }

        return response;
    }


    // =========================== 推送结束 ===============================

    // =========================== 修改 ===============================

    private ContextResult processPulishAfterModifiedByDefinedServerId(String dataId, String groupName, String context,
            String serverId) {
        ContextResult response = new ContextResult();
        // 登录
        if (!login(serverId)) {
            response.setSuccess(false);
            response.setStatusMsg("登录失败,造成错误的原因可能是指定的serverId为空");
            return response;
        }
        if (log.isDebugEnabled())
            log.debug("使用processPulishAfterModifiedByDefinedServerId(" + dataId + "," + groupName + "," + context + ","
                    + serverId + ")进行推送修改");
        // 是否存在此dataId,groupName的数据记录
        ContextResult result = null;
        result = queryByDataIdAndGroupName(dataId, groupName, serverId);
        if (null == result || !result.isSuccess()) {
            response.setSuccess(false);
            response.setStatusMsg("找不到需要修改的数据记录，记录不存在!");
            log.warn("找不到需要修改的数据记录，记录不存在! dataId=" + dataId + ",group=" + groupName + ",serverId=" + serverId);
            return response;
        }
        // 有数据，则修改
        else {
            // 设置参数
        	final Collection<Pair<String, ?>> parameters = new ArrayList<Pair<String, ?>>();
        	parameters.add(Pair.of("method", "updateConfig"));
        	parameters.add(Pair.of("dataId", dataId));
        	parameters.add(Pair.of("group", groupName));
        	parameters.add(Pair.of("content", context));
            
            final HttpInvokeResult httpInvokeResult = HttpClientFactory.getHttpClient().post(getAddr(), "/admin", parameters, require_timeout);
            
            // 配置对象
            ConfigInfo configInfo = new ConfigInfo();
            configInfo.setDataId(dataId);
            configInfo.setGroup(groupName);
            configInfo.setContent(context);
            if (log.isDebugEnabled())
                log.debug("待推送的修改ConfigInfo: " + configInfo);
            // 添加一个配置对象到响应结果中
            response.setConfigInfo(configInfo);
            // 执行方法并返回http状态码
            int status = httpInvokeResult.getStatusCode();
            response.setReceiveResult(httpInvokeResult.getResponseBodyAsString());
            response.setStatusCode(status);
            log.info("状态码：" + status + ",响应结果：" + response.getReceiveResult());
            if (status == Constants.SC_OK) {
                response.setSuccess(true);
                response.setStatusMsg("推送修改处理成功");
                log.info("推送修改处理成功");
            }
            else if (status == Constants.SC_REQUEST_TIMEOUT) {
                response.setSuccess(false);
                response.setStatusMsg("推送修改处理超时，默认超时时间为:" + require_timeout + "毫秒");
                log.error("推送修改处理超时，默认超时时间为:" + require_timeout + "毫秒, dataId=" + dataId + ",group=" + groupName
                        + ",content=" + context + ",serverId=" + serverId);
            }
            else {
                response.setSuccess(false);
                response.setStatusMsg("推送修改处理失败,失败原因请通过ContextResult的getReceiveResult()方法查看");
                log.error("推送修改处理失败:" + response.getReceiveResult() + ",dataId=" + dataId + ",group=" + groupName
                        + ",content=" + context + ",serverId=" + serverId);
            }

            return response;
        }
    }


    // =========================== 修改结束 ===============================

    /**
     * 利用 httpclient实现页面登录
     * 
     * @return 登录结果 true:登录成功,false:登录失败
     */

    private boolean login(String serverId) {
        // serverId 为空判断
        if (StringUtils.isEmpty(serverId) || StringUtils.isBlank(serverId))
            return false;
        DiamondSDKConf defaultConf = diamondSDKConfMaps.get(serverId);
        log.info("[login] 登录使用serverId:" + serverId + ",该环境对象属性：" + defaultConf);
        if (null == defaultConf)
            return false;
        RandomDiamondUtils util = new RandomDiamondUtils();
        // 初始化随机取值器
        util.init(defaultConf.getDiamondConfs());
        if (defaultConf.getDiamondConfs().size() == 0)
            return false;
        boolean flag = false;
        log.info("[randomSequence] 此次访问序列为: " + util.getSequenceToString());
        // 最多重试次数为：某个环境的所有已配置的diamondConf的长度
        while (util.getRetry_times() < util.getMax_times()) {

            // 得到随机取得的diamondConf
            DiamondConf diamondConf = util.generatorOneDiamondConf();
            log.info("第" + util.getRetry_times() + "次尝试:" + diamondConf);
            if (diamondConf == null)
                break;
            lastDiamondConf = diamondConf;
            
            // 设置参数
        	final Collection<Pair<String, ?>> parameters = new ArrayList<Pair<String, ?>>();
        	parameters.add(Pair.of("method", "login"));
        	parameters.add(Pair.of("username", diamondConf.getDiamondUsername()));
        	parameters.add(Pair.of("password", diamondConf.getDiamondPassword()));
            
            final HttpInvokeResult httpInvokeResult = HttpClientFactory.getHttpClient().post(getAddr(), "/login", parameters, require_timeout);
            
            log.info("使用diamondIp: " + diamondConf.getDiamondIp() + ",diamondPort: " + diamondConf.getDiamondPort()
                    + ",diamondUsername: " + diamondConf.getDiamondUsername() + ",diamondPassword: "
                    + diamondConf.getDiamondPassword() + "登录diamondServerUrl: [" + diamondConf.getDiamondConUrl() + "]");

            int state = httpInvokeResult.getStatusCode();
            log.info("登录返回状态码：" + state);
            // 状态码为200，则登录成功,跳出循环并返回true
            if (state == Constants.SC_OK) {
                log.info("第" + util.getRetry_times() + "次尝试成功");
                flag = true;
                break;
            }
			else {
				log.error("登录失败" + httpInvokeResult, httpInvokeResult.getCause());
			}
        }
        if (flag == false) {
            log.error("造成login失败的原因可能是：所有diamondServer的配置环境目前均不可用．serverId=" + serverId);
        }
        return flag;
    }

    static final String LIST_FORMAT_URL =
            "/admin?method=listConfig&group=%s&dataId=%s&pageNo=%d&pageSize=%d";
    static final String LIST_LIKE_FORMAT_URL =
            "/admin?method=listConfigLike&group=%s&dataId=%s&pageNo=%d&pageSize=%d";


    /**
     * 处理查询
     * 
     * @param dataIdPattern
     * @param groupNamePattern
     * @param contentPattern
     * @param serverId
     * @param currentPage
     * @param sizeOfPerPage
     * @return
     */
    @SuppressWarnings("unchecked")
    private PageContextResult<ConfigInfo> processQuery(String dataIdPattern, String groupNamePattern,
            String contentPattern, String serverId, long currentPage, long sizeOfPerPage) {
        PageContextResult<ConfigInfo> response = new PageContextResult<ConfigInfo>();
        // 登录
        if (!login(serverId)) {
            response.setSuccess(false);
            response.setStatusMsg("登录失败,造成错误的原因可能是指定的serverId为空或不存在");
            return response;
        }
        if (log.isDebugEnabled())
            log.debug("使用processQuery(" + dataIdPattern + "," + groupNamePattern + "," + contentPattern + ","
                    + serverId + ")进行查询");
        boolean hasPattern =
                PatternUtils.hasCharPattern(dataIdPattern) || PatternUtils.hasCharPattern(groupNamePattern)
                        || PatternUtils.hasCharPattern(contentPattern);
        String uri = null;
        if (hasPattern) {
            if (!StringUtils.isBlank(contentPattern)) {
                log.warn("注意, 正在根据内容来进行模糊查询, dataIdPattern=" + dataIdPattern + ",groupNamePattern=" + groupNamePattern
                        + ",contentPattern=" + contentPattern);
                // 模糊查询内容，全部查出来
                uri = String.format(LIST_LIKE_FORMAT_URL, groupNamePattern, dataIdPattern, 1, Integer.MAX_VALUE);
            }
            else
                uri = String.format(LIST_LIKE_FORMAT_URL, groupNamePattern, dataIdPattern, currentPage, sizeOfPerPage);
        }
        else {
            uri = String.format(LIST_FORMAT_URL, groupNamePattern, dataIdPattern, currentPage, sizeOfPerPage);
        }

        final HttpInvokeResult httpInvokeResult = HttpClientFactory.getHttpClient().get(getAddr(), uri, configureGetHeaders(), require_timeout);
        
        int status = httpInvokeResult.getStatusCode();
        response.setStatusCode(status);
        switch (status) {
        case Constants.SC_OK:
            String json = "";
            try {
                json = getContent(httpInvokeResult).trim();

                Page<ConfigInfo> page = null;

                if (!json.equals("null")) {
                    page =
                            (Page<ConfigInfo>) JSONUtils.deserializeObject(json,
                                new TypeReference<Page<ConfigInfo>>() {
                                });
                }
                if (page != null) {
                    List<ConfigInfo> diamondData = page.getPageItems();
                    if (!StringUtils.isBlank(contentPattern)) {
                        Pattern pattern = Pattern.compile(contentPattern.replaceAll("\\*", ".*"));
                        List<ConfigInfo> newList = new ArrayList<ConfigInfo>();
                        // 强制排序
                        Collections.sort(diamondData);
                        int totalCount = 0;
                        long begin = sizeOfPerPage * (currentPage - 1);
                        long end = sizeOfPerPage * currentPage;
                        for (ConfigInfo configInfo : diamondData) {
                            if (configInfo.getContent() != null) {
                                Matcher m = pattern.matcher(configInfo.getContent());
                                if (m.find()) {
                                    // 只添加sizeOfPerPage个
                                    if (totalCount >= begin && totalCount < end) {
                                        newList.add(configInfo);
                                    }
                                    totalCount++;
                                }
                            }
                        }
                        page.setPageItems(newList);
                        page.setTotalCount(totalCount);
                    }
                    response.setOriginalDataSize(diamondData.size());
                    response.setTotalCounts(page.getTotalCount());
                    response.setCurrentPage(currentPage);
                    response.setSizeOfPerPage(sizeOfPerPage);
                }
                else {
                    response.setOriginalDataSize(0);
                    response.setTotalCounts(0);
                    response.setCurrentPage(currentPage);
                    response.setSizeOfPerPage(sizeOfPerPage);
                }
                response.operation();
                List<ConfigInfo> pageItems = new ArrayList<ConfigInfo>();
                if (page != null) {
                    pageItems = page.getPageItems();
                }
                response.setDiamondData(pageItems);
                response.setSuccess(true);
                response.setStatusMsg("指定diamond的查询完成");
                log.info("指定diamond的查询完成, url=" + uri);
            }
            catch (Exception e) {
                response.setSuccess(false);
                response.setStatusMsg("反序列化失败,错误信息为：" + e.getLocalizedMessage());
                log.error("反序列化page对象失败, dataId=" + dataIdPattern + ",group=" + groupNamePattern + ",serverId="
                        + serverId + ",json=" + json, e);
            }
            break;
        case Constants.SC_REQUEST_TIMEOUT:
            response.setSuccess(false);
            response.setStatusMsg("查询数据超时" + require_timeout + "毫秒");
            log.error("查询数据超时，默认超时时间为:" + require_timeout + "毫秒, dataId=" + dataIdPattern + ",group="
                    + groupNamePattern + ",serverId=" + serverId);
            break;
        default:
            response.setSuccess(false);
            response.setStatusMsg("查询数据出错，服务器返回状态码为" + status);
            log.error("查询数据出错，状态码为：" + status + ",dataId=" + dataIdPattern + ",group=" + groupNamePattern
                    + ",serverId=" + serverId);
            break;
        }

        return response;
    }


    /**
     * 查看是否为压缩的内容
     * 
     * @param httpInvokeResult
     * @return
     */
    boolean isZipContent(HttpInvokeResult httpInvokeResult) {
        if (null != httpInvokeResult.getHeader(Constants.CONTENT_ENCODING)) {
            String acceptEncoding = httpInvokeResult.getHeader(Constants.CONTENT_ENCODING);
            if (acceptEncoding.toLowerCase().indexOf("gzip") > -1) {
                return true;
            }
        }
        return false;
    }


    /**
     * 获取Response的配置信息
     * 
     * @param httpInvokeResult
     * @return
     */
    String getContent(HttpInvokeResult httpInvokeResult) {
        StringBuilder contentBuilder = new StringBuilder();
        if (isZipContent(httpInvokeResult)) {
            // 处理压缩过的配置信息的逻辑
            InputStream is = null;
            GZIPInputStream gzin = null;
            InputStreamReader isr = null;
            BufferedReader br = null;
            try {
                is = new ByteArrayInputStream(httpInvokeResult.getResponseBody());
                gzin = new GZIPInputStream(is);
                isr = new InputStreamReader(gzin, Constants.ENCODE);
                br = new BufferedReader(isr);
                char[] buffer = new char[4096];
                int readlen = -1;
                while ((readlen = br.read(buffer, 0, 4096)) != -1) {
                    contentBuilder.append(buffer, 0, readlen);
                }
            }
            catch (Exception e) {
                log.error("解压缩失败", e);
            }
            finally {
                try {
                    br.close();
                }
                catch (Exception e1) {
                    // ignore
                }
                try {
                    isr.close();
                }
                catch (Exception e1) {
                    // ignore
                }
                try {
                    gzin.close();
                }
                catch (Exception e1) {
                    // ignore
                }
                try {
                    is.close();
                }
                catch (Exception e1) {
                    // ignore
                }
            }
        }
        else {
            // 处理没有被压缩过的配置信息的逻辑
            String content = null;
            try {
                content = httpInvokeResult.getResponseBodyAsString();
            }
            catch (Exception e) {
                log.error("获取配置信息失败", e);
            }
            if (null == content) {
                return null;
            }
            contentBuilder.append(content);
        }
        return StringEscapeUtils.unescapeHtml4(contentBuilder.toString());
    }


    private Collection<Pair<String, ?>> configureGetHeaders() {
    	final Collection<Pair<String, ?>> headers = new ArrayList<Pair<String, ?>>();
    	headers.add(Pair.of(Constants.ACCEPT_ENCODING, "gzip,deflate"));
    	headers.add(Pair.of("Accept", "application/json"));
    	return headers;
    }


    /**
     * 字段dataId,groupName,context为空验证,有一个为空立即返回false
     * 
     * @param dataId
     * @param groupName
     * @param context
     * @return
     */
    private boolean validate(String dataId, String groupName, String context) {
        if (StringUtils.isEmpty(dataId) || StringUtils.isEmpty(groupName) || StringUtils.isEmpty(context)
                || StringUtils.isBlank(dataId) || StringUtils.isBlank(groupName) || StringUtils.isBlank(context))
            return false;
        return true;
    }


    public synchronized ContextResult unpublish(String serverId, long id) {
        return processDelete(serverId, id);
    }


    /**
     * 处理删除
     * 
     * @param serverId
     * @param id
     * @return
     */
    private ContextResult processDelete(String serverId, long id) {
        ContextResult response = new ContextResult();
        // 登录
        if (!login(serverId)) {
            response.setSuccess(false);
            response.setStatusMsg("登录失败,造成错误的原因可能是指定的serverId为空或不存在");
            return response;
        }
        log.info("使用processDelete(" + serverId + "," + id);
        String uri = "/admin?method=deleteConfig&id=" + id;
       
        final HttpInvokeResult httpInvokeResult = HttpClientFactory.getHttpClient().get(getAddr(), uri, configureGetHeaders(), require_timeout);
        
        int status = httpInvokeResult.getStatusCode();
        response.setStatusCode(status);
        switch (status) {
        case Constants.SC_OK:
            response.setSuccess(true);
            response.setReceiveResult(getContent(httpInvokeResult));
            response.setStatusMsg("删除成功, url=" + uri);
            log.warn("删除配置数据成功, url=" + uri);
            break;
        case Constants.SC_REQUEST_TIMEOUT:
            response.setSuccess(false);
            response.setStatusMsg("删除数据超时" + require_timeout + "毫秒");
            log.error("删除数据超时，默认超时时间为:" + require_timeout + "毫秒, id=" + id + ",serverId=" + serverId);
            break;
        default:
            response.setSuccess(false);
            response.setStatusMsg("删除数据出错，服务器返回状态码为" + status);
            log.error("删除数据出错，状态码为：" + status + ", id=" + id + ",serverId=" + serverId);
            break;
        }

        return response;
    }


    @Override
    public Map<String, DiamondSDKConf> getDiamondSDKConfMaps() {
        return this.diamondSDKConfMaps;
    }


    @Override
    public BatchContextResult<ConfigInfoEx> batchQuery(String serverId, String groupName, List<String> dataIds) {
        // 创建返回结果
        BatchContextResult<ConfigInfoEx> response = new BatchContextResult<ConfigInfoEx>();

        // 判断list是否为null
        if (dataIds == null) {
            log.error("dataId list cannot be null, serverId=" + serverId + ",group=" + groupName);
            response.setSuccess(false);
            response.setStatusMsg("dataId list cannot be null");
            return response;
        }

        // 将dataId的list处理为用一个不可见字符分隔的字符串
        StringBuilder dataIdBuilder = new StringBuilder();
        for (String dataId : dataIds) {
            dataIdBuilder.append(dataId).append(Constants.LINE_SEPARATOR);
        }
        String dataIdStr = dataIdBuilder.toString();
        // 登录
        if (!login(serverId)) {
            response.setSuccess(false);
            response.setStatusMsg("login fail, serverId=" + serverId);
            return response;
        }
        
        // 设置参数
    	final Collection<Pair<String, ?>> parameters = new ArrayList<Pair<String, ?>>();
    	parameters.add(Pair.of("method", "batchQuery"));
    	parameters.add(Pair.of("dataIds", dataIdStr));
    	parameters.add(Pair.of("group", groupName));
        
        final HttpInvokeResult httpInvokeResult = HttpClientFactory.getHttpClient().post(getAddr(), "/admin", parameters, require_timeout);

        // 执行方法并返回http状态码
        int status = httpInvokeResult.getStatusCode();
        response.setStatusCode(status);
        String responseMsg = httpInvokeResult.getResponseBodyAsString();
        response.setResponseMsg(responseMsg);

        if (status == Constants.SC_OK) {
            String json = null;
            try {
                json = responseMsg;

                // 反序列化json字符串, 并将结果处理后放入BatchContextResult中
                List<ConfigInfoEx> configInfoExList = new LinkedList<ConfigInfoEx>();
                Object resultObj = JSONUtils.deserializeObject(json, new TypeReference<List<ConfigInfoEx>>() {
                });
                if (!(resultObj instanceof List<?>)) {
                    throw new RuntimeException("batch query deserialize type error, not list, json=" + json);
                }
                List<ConfigInfoEx> resultList = (List<ConfigInfoEx>) resultObj;
                for (ConfigInfoEx configInfoEx : resultList) {
                    configInfoExList.add(configInfoEx);
                }
                response.getResult().addAll(configInfoExList);

                // 反序列化成功, 本次批量查询成功
                response.setSuccess(true);
                response.setStatusMsg("batch query success");
                log.info("batch query success, serverId=" + serverId + ",dataIds=" + dataIdStr + ",group="
                        + groupName + ",json=" + json);
            }
            catch (Exception e) {
                response.setSuccess(false);
                response.setStatusMsg("batch query deserialize error");
                log.error("batch query deserialize error, serverId=" + serverId + ",dataIdStr=" + dataIdStr
                        + ",group=" + groupName + ",json=" + json, e);
            }

        }
        else if (status == Constants.SC_REQUEST_TIMEOUT) {
            response.setSuccess(false);
            response.setStatusMsg("batch query timeout, socket timeout(ms):" + require_timeout);
            log.error("batch query timeout, socket timeout(ms):" + require_timeout + ", serverId=" + serverId
                    + ",dataIds=" + dataIdStr + ",group=" + groupName);
        }
        else {
            response.setSuccess(false);
            response.setStatusMsg("batch query fail, status:" + status);
            log.error("batch query fail, status:" + status + ", response:" + responseMsg + ",serverId=" + serverId
                    + ",dataIds=" + dataIdStr + ",group=" + groupName);
        }

        return response;
    }


    @Override
    public BatchContextResult<ConfigInfoEx> batchAddOrUpdate(String serverId, String groupName,
            Map<String, String> dataId2ContentMap) {
        // 创建返回结果
        BatchContextResult<ConfigInfoEx> response = new BatchContextResult<ConfigInfoEx>();

        // 判断map是否为null
        if (dataId2ContentMap == null) {
            log.error("dataId2ContentMap cannot be null, serverId=" + serverId + " ,group=" + groupName);
            response.setSuccess(false);
            response.setStatusMsg("dataId2ContentMap cannot be null");
            return response;
        }

        // 将dataId和content的map处理为用一个不可见字符分隔的字符串
        StringBuilder allDataIdAndContentBuilder = new StringBuilder();
        for (String dataId : dataId2ContentMap.keySet()) {
            String content = dataId2ContentMap.get(dataId);
            allDataIdAndContentBuilder.append(dataId + Constants.WORD_SEPARATOR + content).append(
                Constants.LINE_SEPARATOR);
        }
        String allDataIdAndContent = allDataIdAndContentBuilder.toString();

        // 登录
        if (!login(serverId)) {
            response.setSuccess(false);
            response.setStatusMsg("login fail, serverId=" + serverId);
            return response;
        }

        // 设置参数
    	final Collection<Pair<String, ?>> parameters = new ArrayList<Pair<String, ?>>();
    	parameters.add(Pair.of("method", "batchAddOrUpdate"));
    	parameters.add(Pair.of("allDataIdAndContent", allDataIdAndContent));
    	parameters.add(Pair.of("group", groupName));
        
        final HttpInvokeResult httpInvokeResult = HttpClientFactory.getHttpClient().post(getAddr(), "/admin", parameters, require_timeout);

        // 执行方法并返回http状态码
        int status = httpInvokeResult.getStatusCode();
        response.setStatusCode(status);
        String responseMsg = httpInvokeResult.getResponseBodyAsString();
        response.setResponseMsg(responseMsg);

        if (status == Constants.SC_OK) {
            String json = null;
            try {
                json = responseMsg;

                // 反序列化json字符串, 并将结果处理后放入BatchContextResult中
                List<ConfigInfoEx> configInfoExList = new LinkedList<ConfigInfoEx>();
                Object resultObj = JSONUtils.deserializeObject(json, new TypeReference<List<ConfigInfoEx>>() {
                });
                if (!(resultObj instanceof List<?>)) {
                    throw new RuntimeException("batch write deserialize type error, not list, json=" + json);
                }
                List<ConfigInfoEx> resultList = (List<ConfigInfoEx>) resultObj;
                for (ConfigInfoEx configInfoEx : resultList) {
                    configInfoExList.add(configInfoEx);
                }
                response.getResult().addAll(configInfoExList);
                // 反序列化成功, 本次批量操作成功
                response.setStatusMsg("batch write success");
                log.info("batch write success,serverId=" + serverId + ",allDataIdAndContent=" + allDataIdAndContent
                        + ",group=" + groupName + ",json=" + json);
            }
            catch (Exception e) {
                response.setSuccess(false);
                response.setStatusMsg("batch write deserialize error");
                log.error("batch write deserialize error, serverId=" + serverId + ",allDataIdAndContent="
                        + allDataIdAndContent + ",group=" + groupName + ",json=" + json, e);
            }
        }
        else if (status == Constants.SC_REQUEST_TIMEOUT) {
            response.setSuccess(false);
            response.setStatusMsg("batch write timeout, socket timeout(ms):" + require_timeout);
            log.error("batch write timeout, socket timeout(ms):" + require_timeout + ", serverId=" + serverId
                    + ",allDataIdAndContent=" + allDataIdAndContent + ",group=" + groupName);
        }
        else {
            response.setSuccess(false);
            response.setStatusMsg("batch write fail, status:" + status);
            log.error("batch write fail, status:" + status + ", response:" + responseMsg + ",serverId=" + serverId
                    + ",allDataIdAndContent=" + allDataIdAndContent + ",group=" + groupName);
        }

        return response;
    }

}
