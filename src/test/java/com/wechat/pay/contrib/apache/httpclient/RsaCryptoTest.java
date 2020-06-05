package com.wechat.pay.contrib.apache.httpclient;

import static org.junit.Assert.assertTrue;

import com.wechat.pay.contrib.apache.httpclient.auth.AutoUpdateCertificatesVerifier;
import com.wechat.pay.contrib.apache.httpclient.auth.PrivateKeySigner;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Credentials;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Validator;
import com.wechat.pay.contrib.apache.httpclient.util.PemUtil;
import com.wechat.pay.contrib.apache.httpclient.util.RsaCryptoUtil;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.PrivateKey;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RsaCryptoTest {

  private static String mchId = ""; // 商户号
  private static String mchSerialNo = ""; // 商户证书序列号
  private static String apiV3Key = ""; // api密钥
  // 你的商户私钥
  private static String privateKey = "-----BEGIN PRIVATE KEY-----\n"
      + "-----END PRIVATE KEY-----\n";

  private CloseableHttpClient httpClient;
  private AutoUpdateCertificatesVerifier verifier;

  @Before
  public void setup() throws IOException {
    PrivateKey merchantPrivateKey = PemUtil.loadPrivateKey(
        new ByteArrayInputStream(privateKey.getBytes("utf-8")));

    //使用自动更新的签名验证器，不需要传入证书
    verifier = new AutoUpdateCertificatesVerifier(
        new WechatPay2Credentials(mchId, new PrivateKeySigner(mchSerialNo, merchantPrivateKey)),
        apiV3Key.getBytes("utf-8"));

    httpClient = WechatPayHttpClientBuilder.create()
        .withMerchant(mchId, mchSerialNo, merchantPrivateKey)
        .withValidator(new WechatPay2Validator(verifier))
        .build();
  }

  @After
  public void after() throws IOException {
    httpClient.close();
  }

  @Test
  public void encryptTest() throws Exception {
    String text = "helloworld";
    String ciphertext = RsaCryptoUtil.encryptOAEP(text, verifier.getValidCertificate());

    System.out.println("ciphertext: " + ciphertext);
  }

  @Test
  public void postEncryptDataTest() throws Exception {
    HttpPost httpPost = new HttpPost("https://api.mch.weixin.qq.com/v3/smartguide/guides");

    String text = "helloworld";
    String ciphertext = RsaCryptoUtil.encryptOAEP(text, verifier.getValidCertificate());

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
    StringEntity reqEntity = new StringEntity(
        data, ContentType.create("application/json", "utf-8"));
    httpPost.setEntity(reqEntity);
    httpPost.addHeader("Accept", "application/json");
    httpPost.addHeader("Wechatpay-Serial", "5157F09EFDC096DE15EBE81A47057A7232F1B8E1");

    CloseableHttpResponse response = httpClient.execute(httpPost);
    assertTrue(response.getStatusLine().getStatusCode() != 401);
    assertTrue(response.getStatusLine().getStatusCode() != 400);
    try {
      HttpEntity entity2 = response.getEntity();
      // do something useful with the response body
      // and ensure it is fully consumed
      EntityUtils.consume(entity2);
    } finally {
      response.close();
    }
  }
}
