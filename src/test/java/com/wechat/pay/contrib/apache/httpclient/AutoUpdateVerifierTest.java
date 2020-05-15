package com.wechat.pay.contrib.apache.httpclient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.wechat.pay.contrib.apache.httpclient.auth.*;
import com.wechat.pay.contrib.apache.httpclient.util.PemUtil;

import java.io.*;
import java.security.PrivateKey;
import java.security.SecureRandom;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AutoUpdateVerifierTest {

  private static String mchId = ""; // 商户号
  private static String mchSerialNo = ""; // 商户证书序列号
  private static String apiV3Key = ""; // api密钥

  private CloseableHttpClient httpClient;
  private AutoUpdateCertificatesVerifier verifier;

  // 你的商户私钥
  private static String privateKey = "-----BEGIN PRIVATE KEY-----\n"
      + "-----END PRIVATE KEY-----\n";

  //测试AutoUpdateCertificatesVerifier的verify方法参数
  private static String serialNumber = "";
  private static String message = "";
  private static String signature = "";

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
  }

  /**
   * 上传图片 sign不能对HttpRequest加签
   * 加签内容 buildMessage
   * POST
   * /v3/merchant/media/upload
   * 1566987169           //时间戳
   * 12ced2db6f0193dda91ba86224ea1cd8   //随机数
   * {"filename":" filea.jpg ","sha256":" hjkahkjsjkfsjk78687dhjahdajhk "}
   * @throws UnsupportedEncodingException
   */
  @Test
  public void uploadImageTest() throws IOException {
    String filePath="";
    String url="https://api.mch.weixin.qq.com/v3/merchant/media/upload";
    File file=new File(filePath);
    String fileName=file.getName();
    FileInputStream fileInputStream = new FileInputStream(file);
    String sha256Content = DigestUtils.sha256Hex(fileInputStream);

    HttpPost httpPost=new HttpPost(url);
    String json=String.format("{\"filename\":\"%s\",\"sha256\":\"%s\"}",fileName,sha256Content);
    httpPost.addHeader("Authorization",getToken(json));
    httpPost.addHeader("Content-Type", ContentType.MULTIPART_FORM_DATA.toString());
    httpPost.addHeader("Accept",ContentType.APPLICATION_JSON.toString());

    MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create().setMode(HttpMultipartMode.RFC6532);
    entityBuilder.setBoundary("boundary");
    entityBuilder.addTextBody("meta",json, ContentType.APPLICATION_JSON);
    entityBuilder.addBinaryBody("file",file,ContentType.IMAGE_JPEG,fileName);
    httpPost.setEntity(entityBuilder.build());

    HttpResponse httpResponse=httpClient.execute(httpPost);

    String resp= EntityUtils.toString(httpResponse.getEntity());
  }
  private String getToken(String jsonStr) throws UnsupportedEncodingException {
    String nonce = generateNonceStr();
    long timestamp = System.currentTimeMillis() / 1000;
    PrivateKey merchantPrivateKey = PemUtil.loadPrivateKey(
            new ByteArrayInputStream(privateKey.getBytes("utf-8")));
    String message = String.format("POST\n/v3/merchant/media/upload\n%s\n%s\n%s\n",timestamp,nonce,jsonStr
    );
    PrivateKeySigner privateKeySigner=new PrivateKeySigner(mchSerialNo,merchantPrivateKey);
    Signer.SignatureResult signResult=privateKeySigner.sign(message.getBytes("utf-8"));

    String token = "WECHATPAY2-SHA256-RSA2048 mchid=\"" + mchId + "\","
            + "nonce_str=\"" + nonce + "\","
            + "timestamp=\"" + timestamp + "\","
            + "serial_no=\"" + signResult.getCertificateSerialNumber() + "\","
            + "signature=\"" + signResult.getSign() + "\"";
    return token;
  }
  private static final String SYMBOLS =
          "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private static final SecureRandom RANDOM = new SecureRandom();
  protected String generateNonceStr() {
    char[] nonceChars = new char[32];
    for (int index = 0; index < nonceChars.length; ++index) {
      nonceChars[index] = SYMBOLS.charAt(RANDOM.nextInt(SYMBOLS.length()));
    }
    return new String(nonceChars);
  }
}
