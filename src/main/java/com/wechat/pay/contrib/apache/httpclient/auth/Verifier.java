package com.wechat.pay.contrib.apache.httpclient.auth;

import java.security.cert.X509Certificate;
import org.jetbrains.annotations.Nullable;

public interface Verifier {

  boolean verify(String serialNumber, byte[] message, String signature);

  @Nullable
  X509Certificate getValidCertificate();
}
