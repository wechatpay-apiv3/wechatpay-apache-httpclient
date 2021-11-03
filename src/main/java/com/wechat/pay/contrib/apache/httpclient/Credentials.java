package com.wechat.pay.contrib.apache.httpclient;

import java.io.IOException;
import org.apache.http.client.methods.HttpRequestWrapper;

/**
 * @author xy-peng
 */
public interface Credentials {

    String getSchema();

    String getToken(HttpRequestWrapper request) throws IOException;

}
