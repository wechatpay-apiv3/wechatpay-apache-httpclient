package com.wechat.pay.contrib.apache.httpclient.auth;

public interface Signer {
  SignatureResult sign(byte[] message);

  /**
   * 暴露外部访问
   */
  public static class SignatureResult {
    String sign;
    String certificateSerialNumber;

    public SignatureResult(String sign, String serialNumber) {
      this.sign = sign;
      this.certificateSerialNumber = serialNumber;
    }

    public String getSign() {
      return sign;
    }

    public void setSign(String sign) {
      this.sign = sign;
    }

    public String getCertificateSerialNumber() {
      return certificateSerialNumber;
    }

    public void setCertificateSerialNumber(String certificateSerialNumber) {
      this.certificateSerialNumber = certificateSerialNumber;
    }
  }
}
