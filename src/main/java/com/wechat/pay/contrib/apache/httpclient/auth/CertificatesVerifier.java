package com.wechat.pay.contrib.apache.httpclient.auth;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.*;

public class CertificatesVerifier implements Verifier {

  private final HashMap<BigInteger, X509Certificate> certificates = new HashMap<>();

  /**
   * 平台证书对应的证书序列号
   */
  private final HashMap<BigInteger, String> CERTIFICATE_SERIAL_NOS = new HashMap<>();

  /**
   * 当前生效的证书序列号
   */
  private BigInteger effectiveCertSerialNumber;

  public CertificatesVerifier(List<X509Certificate> list) {

    for (X509Certificate item : list) {
      certificates.put(item.getSerialNumber(), item);
    }
  }

    /**
     * 新增够造用于启用自动更新证书方便获取平台证书 serial_no
     * @param list 证书&证书序列号
     */
  public CertificatesVerifier(Map<String, X509Certificate> list) {
    for (Map.Entry<String, X509Certificate> item : list.entrySet()) {
      certificates.put(item.getValue().getSerialNumber(), item.getValue());
      CERTIFICATE_SERIAL_NOS.put(item.getValue().getSerialNumber(), item.getKey());
    }
  }

  private boolean verify(X509Certificate certificate, byte[] message, String signature) {
    try {
      Signature sign = Signature.getInstance("SHA256withRSA");
      sign.initVerify(certificate);
      sign.update(message);
      return sign.verify(Base64.getDecoder().decode(signature));
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("当前Java环境不支持SHA256withRSA", e);
    } catch (SignatureException e) {
      throw new RuntimeException("签名验证过程发生了错误", e);
    } catch (InvalidKeyException e) {
      throw new RuntimeException("无效的证书", e);
    }
  }

  @Override
  public boolean verify(String serialNumber, byte[] message, String signature) {
    BigInteger val = new BigInteger(serialNumber, 16);
    return certificates.containsKey(val) && verify(certificates.get(val), message, signature);
  }

  @Override
  public X509Certificate getValidCertificate() {
    for (Map.Entry<BigInteger, X509Certificate> certs : certificates.entrySet()) {
      try {
        certs.getValue().checkValidity();
        effectiveCertSerialNumber = certs.getKey();
        return certs.getValue();
      } catch (CertificateExpiredException | CertificateNotYetValidException ignored) {
      }
    }

    throw new NoSuchElementException("没有有效的微信支付平台证书");
  }

  @Override
  public String getValidCertificateSerialNo() {
    if (effectiveCertSerialNumber == null) {
      getValidCertificate();
    }
    if (effectiveCertSerialNumber == null) {
      return null;
    }
    return CERTIFICATE_SERIAL_NOS.get(effectiveCertSerialNumber);
  }
}
