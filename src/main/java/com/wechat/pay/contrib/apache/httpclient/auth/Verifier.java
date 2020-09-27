package com.wechat.pay.contrib.apache.httpclient.auth;

import java.security.cert.X509Certificate;

public interface Verifier {

  boolean verify(String serialNumber, byte[] message, String signature);

  X509Certificate getValidCertificate();

  /**
   * 获取平台证书对应的SerialNo <p>
   * 仅适用于开启了自动更新证书可以获取
   * @return CertificateSerialNo
   */
  String getValidCertificateSerialNo();
}
