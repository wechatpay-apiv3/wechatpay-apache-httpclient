package com.wechat.pay.contrib.apache.httpclient.auth;

public interface Verifier {
  boolean verify(String serialNumber, byte[] message, String signature);
}
