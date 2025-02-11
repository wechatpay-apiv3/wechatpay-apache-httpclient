package com.wechat.pay.contrib.apache.httpclient;

import com.wechat.pay.contrib.apache.httpclient.auth.MixVerifier;
import com.wechat.pay.contrib.apache.httpclient.auth.PrivateKeySigner;
import com.wechat.pay.contrib.apache.httpclient.auth.PublicKeyVerifier;
import com.wechat.pay.contrib.apache.httpclient.auth.Verifier;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Credentials;
import com.wechat.pay.contrib.apache.httpclient.cert.CertificatesManager;
import com.wechat.pay.contrib.apache.httpclient.notification.Notification;
import com.wechat.pay.contrib.apache.httpclient.notification.NotificationHandler;
import com.wechat.pay.contrib.apache.httpclient.notification.NotificationRequest;
import com.wechat.pay.contrib.apache.httpclient.util.PemUtil;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class NotificationHandlerTest {

    private static final String privateKey = "-----BEGIN PRIVATE KEY-----\n"
            + "-----END PRIVATE KEY-----\n"; // 商户私钥
    private static final String merchantId = ""; // 商户号
    private static final String merchantSerialNumber = ""; // 商户证书序列号
    private static final String apiV3Key = ""; // apiV3密钥
    private static final String wechatPaySerial = ""; // 平台证书序列号
    private static final String wechatPayPublicKeyStr = "-----BEGIN PUBLIC KEY-----\n"
            + "-----END PUBLIC KEY-----"; // 微信支付公钥
    private static final String wechatpayPublicKeyId = "PUB_KEY_ID_"; // 微信支付公钥ID
    private static final String nonce = ""; // 请求头Wechatpay-Nonce
    private static final String timestamp = "";// 请求头Wechatpay-Timestamp
    private static final String signature = "";// 请求头Wechatpay-Signature
    private static final String body = ""; // 请求体
    private Verifier publicKeyVerifier; // 微信支付公钥验签器
    private Verifier certificateVerifier; // 平台证书验签器
    private static CertificatesManager certificatesManager; // 平台证书管理器

    @Before
    public void setup() throws Exception {
        PrivateKey merchantPrivateKey = PemUtil.loadPrivateKey(privateKey);
        // 获取证书管理器实例
        certificatesManager = CertificatesManager.getInstance();
        // 向证书管理器增加需要自动更新平台证书的商户信息
        certificatesManager.putMerchant(merchantId, new WechatPay2Credentials(merchantId,
                        new PrivateKeySigner(merchantSerialNumber, merchantPrivateKey)),
                apiV3Key.getBytes(StandardCharsets.UTF_8));
        // 从证书管理器中获取verifier
        certificateVerifier = certificatesManager.getVerifier(merchantId);
        // 创建公钥验签器
        PublicKey wechatPayPublicKey = PemUtil.loadPublicKey(wechatPayPublicKeyStr);
        publicKeyVerifier = new PublicKeyVerifier(wechatpayPublicKeyId, wechatPayPublicKey);
    }

    private NotificationRequest buildNotificationRequest() {
        return new NotificationRequest.Builder().withSerialNumber(wechatPaySerial)
                .withNonce(nonce)
                .withTimestamp(timestamp)
                .withSignature(signature)
                .withBody(body)
                .build();
    }

    @Test
    public void handleNotificationWithPublicKeyVerifier() throws Exception {
        NotificationRequest request = buildNotificationRequest();

        // 使用微信支付公钥验签器：适用于已经完成「平台证书」-->「微信支付公钥」迁移的商户以及新申请的商户
        NotificationHandler handler = new NotificationHandler(certificateVerifier, apiV3Key.getBytes(StandardCharsets.UTF_8));

        // 验签和解析请求体
        Notification notification = handler.parse(request);
        Assert.assertNotNull(notification);
        System.out.println(notification.toString());
    }

    @Test
    public void handleNotificationWithMixVerifier() throws Exception {
        NotificationRequest request = buildNotificationRequest();

        // 使用混合验签器：适用于正在进行「平台证书」-->「微信支付公钥」迁移的商户
        Verifier mixVerifier = new MixVerifier((PublicKeyVerifier) publicKeyVerifier, certificateVerifier);
        NotificationHandler handler = new NotificationHandler(mixVerifier, apiV3Key.getBytes(StandardCharsets.UTF_8));

        // 验签和解析请求体
        Notification notification = handler.parse(request);
        Assert.assertNotNull(notification);
        System.out.println(notification.toString());
    }

    @Test
    public void handleNotificationWithCertificateVerifier() throws Exception {
        NotificationRequest request = buildNotificationRequest();

        // 使用平台证书验签器：适用于尚未开始「平台证书」-->「微信支付公钥」迁移的旧商户
        NotificationHandler handler = new NotificationHandler(certificateVerifier, apiV3Key.getBytes(StandardCharsets.UTF_8));

        // 验签和解析请求体
        Notification notification = handler.parse(request);
        Assert.assertNotNull(notification);
        System.out.println(notification.toString());
    }
}
