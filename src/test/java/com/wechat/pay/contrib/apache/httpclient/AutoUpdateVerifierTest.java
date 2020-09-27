package com.wechat.pay.contrib.apache.httpclient;

import com.wechat.pay.contrib.apache.httpclient.auth.AutoUpdateCertificatesVerifier;
import com.wechat.pay.contrib.apache.httpclient.auth.PrivateKeySigner;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Credentials;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Validator;
import com.wechat.pay.contrib.apache.httpclient.util.PemUtil;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.net.URI;
import java.security.PrivateKey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AutoUpdateVerifierTest {

  private static String mchId = ""; // 商户号
  private static String mchSerialNo = ""; // 商户证书序列号
  private static String apiV3Key = ""; // api密钥
  // 你的商户私钥
  private static String privateKey = "-----BEGIN PRIVATE KEY-----\n"
      + "-----END PRIVATE KEY-----\n";

  //测试AutoUpdateCertificatesVerifier的verify方法参数
  private static String serialNumber = "";
  private static String message = "";
  private static String signature = "";
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
  public void autoUpdateVerifierTest() throws Exception {
    assertTrue(verifier.verify(serialNumber, message.getBytes("utf-8"), signature));
  }

  @Test
  public void getCertificateTest() throws Exception {
    URIBuilder uriBuilder = new URIBuilder("https://api.mch.weixin.qq.com/v3/certificates");
    HttpGet httpGet = new HttpGet(uriBuilder.build());
    httpGet.addHeader("Accept", "application/json");
    CloseableHttpResponse response1 = httpClient.execute(httpGet);
    assertEquals(200, response1.getStatusLine().getStatusCode());
    try {
      HttpEntity entity1 = response1.getEntity();
      // do something useful with the response body
      // and ensure it is fully consumed
      EntityUtils.consume(entity1);
    } finally {
      response1.close();
    }
    System.out.println("获取自动管理的平台证书序列号: " + verifier.getValidCertificateSerialNo());
  }

  @Test
  public void uploadImageTest() throws Exception {
    String filePath = "/your/home/hellokitty.png";

    URI uri = new URI("https://api.mch.weixin.qq.com/v3/merchant/media/upload");

    File file = new File(filePath);
    try (FileInputStream s1 = new FileInputStream(file)) {
      String sha256 = DigestUtils.sha256Hex(s1);
      try (InputStream s2 = new FileInputStream(file)) {
        WechatPayUploadHttpPost request = new WechatPayUploadHttpPost.Builder(uri)
            .withImage(file.getName(), sha256, s2)
            .build();

        CloseableHttpResponse response1 = httpClient.execute(request);
        assertEquals(200, response1.getStatusLine().getStatusCode());
        try {
          HttpEntity entity1 = response1.getEntity();
          // do something useful with the response body
          // and ensure it is fully consumed
          String s = EntityUtils.toString(entity1);
          System.out.println(s);
        } finally {
          response1.close();
        }
      }
    }
  }
}
