package com.wechat.pay.contrib.apache.httpclient;

import com.wechat.pay.contrib.apache.httpclient.util.PemUtil;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class HttpClientBuilderTest {

  private static String mchId = "1900009191"; // 商户号
  private static String mchSerialNo = "1DDE55AD98ED71D6EDD4A4A16996DE7B47773A8C"; // 商户证书序列号

  private CloseableHttpClient httpClient;

  private static String reqdata = "{\n"
      + "    \"stock_id\": \"9433645\",\n"
      + "    \"stock_creator_mchid\": \"1900006511\",\n"
      + "    \"out_request_no\": \"20190522_001中文11\",\n"
      + "    \"appid\": \"wxab8acb865bb1637e\"\n"
      + "}";

  // 你的商户私钥
  private static String privateKey = "-----BEGIN PRIVATE KEY-----\n"
      + "-----END PRIVATE KEY-----";

  // 你的微信支付平台证书
  private static String certificate =  "-----BEGIN CERTIFICATE-----\n"
      + "-----END CERTIFICATE-----";

  @Before
  public void setup() throws IOException  {
    PrivateKey merchantPrivateKey = PemUtil.loadPrivateKey(
        new ByteArrayInputStream(privateKey.getBytes("utf-8")));
    X509Certificate wechatpayCertificate = PemUtil.loadCertificate(
        new ByteArrayInputStream(certificate.getBytes("utf-8")));

    ArrayList<X509Certificate> listCertificates = new ArrayList<>();
    listCertificates.add(wechatpayCertificate);

    httpClient = WechatPayHttpClientBuilder.create()
        .withMerchant(mchId, mchSerialNo, merchantPrivateKey)
        .withWechatpay(listCertificates)
        .build();
  }

  @After
  public void after() throws IOException {
    httpClient.close();
  }

  @Test
  public void getCertificateTest() throws Exception {
    URIBuilder uriBuilder = new URIBuilder("https://api.mch.weixin.qq.com/v3/certificates");
    uriBuilder.setParameter("p", "1&2");
    uriBuilder.setParameter("q", "你好");

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
  }

  @Test
  public void getCertificatesWithoutCertTest() throws Exception {
    PrivateKey merchantPrivateKey = PemUtil.loadPrivateKey(
        new ByteArrayInputStream(privateKey.getBytes("utf-8")));

    httpClient = WechatPayHttpClientBuilder.create()
        .withMerchant(mchId, mchSerialNo, merchantPrivateKey)
        .withValidator(response -> true)
        .build();

    getCertificateTest();
  }

  @Test
  public void postNonRepeatableEntityTest() throws IOException {
    HttpPost httpPost = new HttpPost(
        "https://api.mch.weixin.qq.com/v3/marketing/favor/users/oHkLxt_htg84TUEbzvlMwQzVDBqo/coupons");


    InputStream stream = new ByteArrayInputStream(reqdata.getBytes("utf-8"));
    InputStreamEntity reqEntity = new InputStreamEntity(stream);
    reqEntity.setContentType("application/json");
    httpPost.setEntity(reqEntity);
    httpPost.addHeader("Accept", "application/json");

    CloseableHttpResponse response = httpClient.execute(httpPost);
    assertTrue(response.getStatusLine().getStatusCode() != 401);
    try {
      HttpEntity entity2 = response.getEntity();
      // do something useful with the response body
      // and ensure it is fully consumed
      EntityUtils.consume(entity2);
    } finally {
      response.close();
    }
  }

  @Test
  public void postRepeatableEntityTest() throws IOException {
    HttpPost httpPost = new HttpPost(
        "https://api.mch.weixin.qq.com/v3/marketing/favor/users/oHkLxt_htg84TUEbzvlMwQzVDBqo/coupons");

    // NOTE: 建议指定charset=utf-8。低于4.4.6版本的HttpCore，不能正确的设置字符集，可能导致签名错误
    StringEntity reqEntity = new StringEntity(
        reqdata, ContentType.create("application/json", "utf-8"));
    httpPost.setEntity(reqEntity);
    httpPost.addHeader("Accept", "application/json");

    CloseableHttpResponse response = httpClient.execute(httpPost);
    assertTrue(response.getStatusLine().getStatusCode() != 401);
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
