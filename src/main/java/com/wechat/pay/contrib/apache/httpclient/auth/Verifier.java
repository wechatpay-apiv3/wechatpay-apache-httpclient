package com.wechat.pay.contrib.apache.httpclient.auth;

import java.security.cert.X509Certificate;

/**
 * @author xy-peng
 */
public interface Verifier {

    boolean verify(String serialNumber, byte[] message, String signature);

    /**
     * 该方法已废弃，请使用getLatestCertificate代替
     *
     * @return 合法证书
     */
    @Deprecated
    X509Certificate getValidCertificate();

    X509Certificate getLatestCertificate();

}
