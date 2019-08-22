package com.wechat.pay.contrib.apache.httpclient.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wechat.pay.contrib.apache.httpclient.Credentials;
import com.wechat.pay.contrib.apache.httpclient.WechatPayHttpClientBuilder;
import com.wechat.pay.contrib.apache.httpclient.util.AesUtil;
import com.wechat.pay.contrib.apache.httpclient.util.PemUtil;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 在原有CertificatesVerifier基础上，增加自动更新证书功能
 */
public class AutoUpdateCertificatesVerifier implements Verifier {

  private static final Logger log = LoggerFactory.getLogger(AutoUpdateCertificatesVerifier.class);

  //证书下载地址
  private static final String CertDownloadPath = "https://api.mch.weixin.qq.com/v3/certificates";

  //上次更新时间
  private volatile Instant instant;

  //证书更新间隔时间，单位为分钟
  private int minutesInterval;

  private CertificatesVerifier verifier;

  private List<X509Certificate> certList;

  private Credentials credentials;

  private byte[] apiV3Key;

  private ReentrantLock lock = new ReentrantLock();

  public AutoUpdateCertificatesVerifier(List<X509Certificate> certList, Credentials credentials,
      byte[] apiV3Key) {
    //默认证书更新时间为1小时
    this(certList, credentials, apiV3Key, 60);
  }

  public AutoUpdateCertificatesVerifier(List<X509Certificate> certList, Credentials credentials,
      byte[] apiV3Key, int minutesInterval) {
    this.certList = certList;
    this.verifier = new CertificatesVerifier(certList);
    this.credentials = credentials;
    this.apiV3Key = apiV3Key;
    this.instant = Instant.now();
    this.minutesInterval = minutesInterval;
  }

  @Override
  public boolean verify(String serialNumber, byte[] message, String signature) {
    if (Duration.between(Instant.now(), instant).toMinutes() >= minutesInterval) {
      if (lock.tryLock()) {
        try {
          autoUpdateCert();
          //更新时间
          instant = Instant.now();
        } finally {
          lock.unlock();
        }
      }
    }
    return verifier.verify(serialNumber, message, signature);
  }

  private void autoUpdateCert() {
    CloseableHttpClient httpClient = WechatPayHttpClientBuilder.create()
        .withCredentials(credentials)
        .withValidator(new WechatPay2Validator(new CertificatesVerifier(this.certList)))
        .build();

    try {
      HttpGet httpGet = new HttpGet(CertDownloadPath);
      httpGet.addHeader("Accept", "application/json");

      CloseableHttpResponse response = httpClient.execute(httpGet);
      int statusCode = response.getStatusLine().getStatusCode();
      String body = EntityUtils.toString(response.getEntity());
      if (statusCode == 200) {
        List<X509Certificate> newCertList = deserializeToCerts(apiV3Key, body);
        if (newCertList.isEmpty()) {
          log.warn("Cert list is empty");
          return;
        }

        mergeAndCheckCerts(newCertList);
      } else {
        log.warn("Auto update cert failed, statusCode = " + statusCode + ",body = " + body);
      }
    } catch (GeneralSecurityException | IOException e) {
      log.warn("Auto update cert failed, exception = " + e);
    }
  }

  /**
   * 合并证书并检查是否过期
   */
  private void mergeAndCheckCerts(List<X509Certificate> newCertList) {
    //去重
    Set<X509Certificate> set = new HashSet<>();
    set.addAll(newCertList);
    set.addAll(this.certList);
    this.certList = new ArrayList<>(set);
    //去掉过期证书
    Iterator<X509Certificate> iterator = certList.iterator();
    while (iterator.hasNext()) {
      X509Certificate cert = iterator.next();
      try {
        cert.checkValidity();
      } catch (CertificateExpiredException | CertificateNotYetValidException e) {
        iterator.remove();
      }
    }
    this.verifier = new CertificatesVerifier(this.certList);
  }

  /**
   * 反序列化证书并解密
   */
  private List<X509Certificate> deserializeToCerts(byte[] apiV3Key, String body)
      throws GeneralSecurityException, IOException {
    //AES加密
    AesUtil decryptor = new AesUtil(apiV3Key);
    ObjectMapper mapper = new ObjectMapper();
    JsonNode dataNode = mapper.readTree(body).get("data");
    List<X509Certificate> newCertList = new ArrayList<>();
    if (dataNode != null) {
      for (int i = 0, count = dataNode.size(); i < count; i++) {
        JsonNode encryptCertificateNode = dataNode.get(i).get("encrypt_certificate");
        //解密
        String cert = decryptor.decryptToString(
            encryptCertificateNode.get("associated_data").toString().replaceAll("\"", "")
                .getBytes("utf-8"),
            encryptCertificateNode.get("nonce").toString().replaceAll("\"", "")
                .getBytes("utf-8"),
            encryptCertificateNode.get("ciphertext").toString().replaceAll("\"", ""));

        X509Certificate x509Cert = PemUtil
            .loadCertificate(new ByteArrayInputStream(cert.getBytes("utf-8")));
        newCertList.add(x509Cert);
      }
    }
    return newCertList;
  }
}
