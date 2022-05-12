package com.wechat.pay.contrib.apache.httpclient;

import com.wechat.pay.contrib.apache.httpclient.auth.PrivateKeySigner;
import com.wechat.pay.contrib.apache.httpclient.auth.Verifier;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Credentials;
import com.wechat.pay.contrib.apache.httpclient.cert.CertificatesManager;
import com.wechat.pay.contrib.apache.httpclient.notification.Notification;
import com.wechat.pay.contrib.apache.httpclient.notification.NotificationHandler;
import com.wechat.pay.contrib.apache.httpclient.notification.NotificationRequest;
import com.wechat.pay.contrib.apache.httpclient.util.PemUtil;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
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
    private static final String nonce = ""; // 请求头Wechatpay-Nonce
    private static final String timestamp = "";// 请求头Wechatpay-Timestamp
    private static final String signature = "";// 请求头Wechatpay-Signature
    private static final String body = ""; // 请求体
    private Verifier verifier; // 验签器
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
        verifier = certificatesManager.getVerifier(merchantId);
    }

    @Test
    public void notificationHandlerTest() throws Exception {
        // 构建request，传入必要参数
        NotificationRequest request = new NotificationRequest.Builder().withSerialNumber(wechatPaySerial)
                .withNonce(nonce)
                .withTimestamp(timestamp)
                .withSignature(signature)
                .withBody(body)
                .build();
        NotificationHandler handler = new NotificationHandler(verifier, apiV3Key.getBytes(StandardCharsets.UTF_8));
        // 验签和解析请求体
        Notification notification = handler.parse(request);
        Assert.assertNotNull(notification);
        System.out.println(notification.toString());
    }
}
