package com.wechat.pay.contrib.apache.httpclient.auth;

import java.security.cert.X509Certificate;

/**
 * @author xy-peng
 */
public interface Verifier {

    boolean verify(String serialNumber, byte[] message, String signature);

    /**
     * 获取合法的平台证书
     *
     * @return 合法证书
     */
    X509Certificate getValidCertificate();
}
