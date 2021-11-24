package com.wechat.pay.contrib.apache.httpclient.auth;

import com.wechat.pay.contrib.apache.httpclient.Credentials;
import com.wechat.pay.contrib.apache.httpclient.cert.CertManagerSingleton;
import java.security.cert.X509Certificate;

/**
 * 在原有 CertificatesVerifier 基础上，增加定时更新证书功能（默认1天）
 */
public class ScheduleUpdateCertificatesVerifier implements Verifier {

    private static final int UPDATE_INTERVAL_MINUTE = 1440;
    private final CertManagerSingleton certManagerSingleton;

    public ScheduleUpdateCertificatesVerifier() {
        certManagerSingleton = CertManagerSingleton.getInstance();
    }

    /**
     * 开始定时更新平台证书
     *
     * @param credentials
     * @param apiv3Key
     */
    public void beginScheduleUpdate(Credentials credentials, byte[] apiv3Key) {
        if (credentials == null || apiv3Key.length == 0) {
            throw new IllegalArgumentException("credentials 或 apiv3Key 为空");
        }
        certManagerSingleton.init(credentials, apiv3Key, UPDATE_INTERVAL_MINUTE);
    }

    @Override
    public X509Certificate getLatestCertificate() {
        if (!this.certManagerSingleton.isInit()) {
            throw new IllegalStateException("请先调用 beginScheduleUpdate 开启定时更新能力");
        }
        return certManagerSingleton.getLatestCertificate();
    }

    @Override
    public boolean verify(String serialNumber, byte[] message, String signature) {
        if (serialNumber.isEmpty() || message.length == 0 || signature.isEmpty()) {
            throw new IllegalArgumentException("serialNumber 或 message 或 signature 为空");
        }
        if (!this.certManagerSingleton.isInit()) {
            throw new IllegalStateException("请先调用 beginScheduleUpdate 开启定时更新能力");
        }
        Verifier verifier = new CertificatesVerifier(certManagerSingleton.getCertificates());
        return verifier.verify(serialNumber, message, signature);
    }

    /**
     * 该方法已废弃，请勿使用
     *
     * @return null
     */
    @Deprecated
    @Override
    public X509Certificate getValidCertificate() {
        return null;
    }


    /**
     * 暂停定时更新
     */
    public void stopScheduleUpdate() {
        if (!this.certManagerSingleton.isInit()) {
            throw new IllegalStateException("请先调用 beginScheduleUpdate 开启定时更新能力");
        }
        this.certManagerSingleton.close();
    }

}