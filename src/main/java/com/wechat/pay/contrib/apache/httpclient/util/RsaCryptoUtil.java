package com.wechat.pay.contrib.apache.httpclient.util;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Base64;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class RsaCryptoUtil {

  public static String encryptOAEP(String message, X509Certificate certificate)
      throws IllegalBlockSizeException {
    try {
      Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
      cipher.init(Cipher.ENCRYPT_MODE, certificate.getPublicKey());

      byte[] data = message.getBytes(StandardCharsets.UTF_8);
      byte[] ciphertext = cipher.doFinal(data);
      return Base64.getEncoder().encodeToString(ciphertext);
    } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
      throw new RuntimeException("当前Java环境不支持RSA v1.5/OAEP", e);
    } catch (InvalidKeyException e) {
      throw new IllegalArgumentException("无效的证书", e);
    } catch (IllegalBlockSizeException | BadPaddingException e) {
      throw new IllegalBlockSizeException("加密原串的长度不能超过214字节");
    }
  }

  public static String decryptOAEP(String ciphertext, PrivateKey privateKey)
      throws BadPaddingException {
    try {
      Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
      cipher.init(Cipher.DECRYPT_MODE, privateKey);

      byte[] data = Base64.getDecoder().decode(ciphertext);
      return new String(cipher.doFinal(data), StandardCharsets.UTF_8);
    } catch (NoSuchPaddingException | NoSuchAlgorithmException e) {
      throw new RuntimeException("当前Java环境不支持RSA v1.5/OAEP", e);
    } catch (InvalidKeyException e) {
      throw new IllegalArgumentException("无效的私钥", e);
    } catch (BadPaddingException | IllegalBlockSizeException e) {
      throw new BadPaddingException("解密失败");
    }
  }
}
