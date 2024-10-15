package com.wechat.pay.contrib.apache.httpclient.proxy;

import org.apache.http.HttpHost;

/**
 * HttpProxyFactory 代理工厂
 *
 * @author ramzeng
 */
public interface HttpProxyFactory {

    /**
     * 构建代理
     *
     * @return 代理
     */
    HttpHost buildHttpProxy();
}
