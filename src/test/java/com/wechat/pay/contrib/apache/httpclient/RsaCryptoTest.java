package com.wechat.pay.contrib.apache.httpclient;

import static com.wechat.pay.contrib.apache.httpclient.constant.WechatPayHttpHeaders.WECHAT_PAY_SERIAL;
import static org.apache.http.HttpHeaders.ACCEPT;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.junit.Assert.assertTrue;

import com.wechat.pay.contrib.apache.httpclient.auth.PrivateKeySigner;
import com.wechat.pay.contrib.apache.httpclient.auth.Verifier;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Credentials;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Validator;
import com.wechat.pay.contrib.apache.httpclient.cert.CertificatesManager;
import com.wechat.pay.contrib.apache.httpclient.util.PemUtil;
import com.wechat.pay.contrib.apache.httpclient.util.RsaCryptoUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.management.PersistentMBean;

public class RsaCryptoTest {

    private static final String mchId = ""; // 商户号
    private static final String mchSerialNo = ""; // 商户证书序列号
    private static final String apiV3Key = ""; // API V3密钥
    private static final String privateKey = ""; // 商户API V3私钥
    private static final String wechatPaySerial = ""; // 平台证书序列号

    private static final String certForEncrypt = ""; // 用于测试加密功能的证书
    private static final String privateKeyForDecrypt = ""; // 用于测试解密功能的私钥

    private CloseableHttpClient httpClient;
    private CertificatesManager certificatesManager;
    private Verifier verifier;

    @Before
    public void setup() throws Exception {
        PrivateKey merchantPrivateKey = PemUtil.loadPrivateKey(privateKey);
        // 获取证书管理器实例
        certificatesManager = CertificatesManager.getInstance();
        // 向证书管理器增加需要自动更新平台证书的商户信息
        certificatesManager.putMerchant(mchId, new WechatPay2Credentials(mchId,
                new PrivateKeySigner(mchSerialNo, merchantPrivateKey)), apiV3Key.getBytes(StandardCharsets.UTF_8));
        // 从证书管理器中获取verifier
        verifier = certificatesManager.getVerifier(mchId);
        httpClient = WechatPayHttpClientBuilder.create()
                .withMerchant(mchId, mchSerialNo, merchantPrivateKey)
                .withValidator(new WechatPay2Validator(certificatesManager.getVerifier(mchId)))
                .build();
    }

    @After
    public void after() throws IOException {
        httpClient.close();
    }

    @Test
    public void encryptTest() throws Exception {
        String text = "helloworld";
        String ciphertext = RsaCryptoUtil
                .encryptOAEP(text, verifier.getValidCertificate());
        System.out.println("ciphertext: " + ciphertext);
    }

    @Test
    public void encryptWithTransformationTest() throws IllegalBlockSizeException {
        String text = "helloworld";
        String transformation = "RSA/ECB/PKCS1Padding";
        String encryptedText = RsaCryptoUtil.encrypt(text, PemUtil.loadCertificate(new ByteArrayInputStream(certForEncrypt.getBytes())), transformation);
        System.out.println("encrypted text: " + encryptedText);
    }

    @Test
    public void decryptWithTransformationTest() throws BadPaddingException {
        String encryptedText = "L8h0PTZFxq1xelmKLwt7KukA2ghtAEImCD19sE7kAjE9kEb7cpWrK73SsA=="; // 需替换为正确加密后的字符串。
        String transformation = "RSA/ECB/PKCS1Padding";
        System.out.println("decrypted text: " + RsaCryptoUtil.decrypt(encryptedText, PemUtil.loadPrivateKey(privateKeyForDecrypt), transformation));
    }

    @Test
    public void postEncryptDataTest() throws Exception {
        HttpPost httpPost = new HttpPost("https://api.mch.weixin.qq.com/v3/smartguide/guides");

        String text = "helloworld";
        String ciphertext = RsaCryptoUtil
                .encryptOAEP(text, verifier.getValidCertificate());

        String data = "{\n"
                + "  \"store_id\" : 1234,\n"
                + "  \"corpid\" : \"1234567890\",\n"
                + "  \"name\" : \"" + ciphertext + "\",\n"
                + "  \"mobile\" : \"" + ciphertext + "\",\n"
                + "  \"qr_code\" : \"https://open.work.weixin.qq.com/wwopen/userQRCode?vcode=xxx\",\n"
                + "  \"sub_mchid\" : \"1234567890\",\n"
                + "  \"avatar\" : \"logo\",\n"
                + "  \"userid\" : \"robert\"\n"
                + "}";
        StringEntity reqEntity = new StringEntity(data, APPLICATION_JSON);
        httpPost.setEntity(reqEntity);
        httpPost.addHeader(ACCEPT, APPLICATION_JSON.toString());
        httpPost.addHeader(WECHAT_PAY_SERIAL, wechatPaySerial);

        CloseableHttpResponse response = httpClient.execute(httpPost);
        assertTrue(response.getStatusLine().getStatusCode() != SC_UNAUTHORIZED);
        assertTrue(response.getStatusLine().getStatusCode() != SC_BAD_REQUEST);
        try {
            HttpEntity entity = response.getEntity();
            // do something useful with the response body
            // and ensure it is fully consumed
            EntityUtils.consume(entity);
        } finally {
            response.close();
        }
    }
}
