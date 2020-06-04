package com.wechat.pay.contrib.apache.httpclient.auth;


import com.wechat.pay.contrib.apache.httpclient.Credentials;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.security.SecureRandom;

import com.wechat.pay.contrib.apache.httpclient.auth.Signer;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * @author abel lee
 * @create 2020-06-04 23:54
 **/

public class WechatPay2Credentials implements Credentials {
  private static final Logger log = LoggerFactory.getLogger(com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Credentials.class);

  private static final String SYMBOLS =
    "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private static final SecureRandom RANDOM = new SecureRandom();
  protected String merchantId;
  protected Signer signer;
  protected String fileUploadBoundary;


  public WechatPay2Credentials(String merchantId, Signer signer) {
    this.merchantId = merchantId;
    this.signer = signer;
    this.fileUploadBoundary = "boundary";
  }

  public WechatPay2Credentials(String merchantId, Signer signer, String fileUploadBoundary) {
    this.merchantId = merchantId;
    this.signer = signer;
    this.fileUploadBoundary = fileUploadBoundary;
  }

  public String getMerchantId() {
    return merchantId;
  }

  protected long generateTimestamp() {
    return System.currentTimeMillis() / 1000;
  }

  protected String generateNonceStr() {
    char[] nonceChars = new char[32];
    for (int index = 0; index < nonceChars.length; ++index) {
      nonceChars[index] = SYMBOLS.charAt(RANDOM.nextInt(SYMBOLS.length()));
    }
    return new String(nonceChars);
  }

  @Override
  public final String getSchema() {
    return "WECHATPAY2-SHA256-RSA2048";
  }

  @Override
  public final String getToken(HttpUriRequest request) throws IOException {
    String nonceStr = generateNonceStr();
    long timestamp = generateTimestamp();

    String message = buildMessage(nonceStr, timestamp, request);
    log.debug("authorization message=[{}]", message);

    Signer.SignatureResult signature = signer.sign(message.getBytes("utf-8"));

    String token = "mchid=\"" + getMerchantId() + "\","
      + "nonce_str=\"" + nonceStr + "\","
      + "timestamp=\"" + timestamp + "\","
      + "serial_no=\"" + signature.certificateSerialNumber + "\","
      + "signature=\"" + signature.sign + "\"";
    log.debug("authorization token=[{}]", token);

    return token;
  }

  protected final String buildMessage(String nonce, long timestamp, HttpUriRequest request)
    throws IOException {
    URI uri = request.getURI();
    String canonicalUrl = uri.getRawPath();
    if (uri.getQuery() != null) {
      canonicalUrl += "?" + uri.getRawQuery();
    }

    String body = "";
    // PATCH,POST,PUT
    if (request instanceof HttpEntityEnclosingRequestBase) {
      if(canonicalUrl.equals("/v3/merchant/media/upload")){
        HttpEntityEnclosingRequestBase requestBase = (HttpEntityEnclosingRequestBase) request;
        InputStream inputStream = requestBase.getEntity().getContent();
        StringBuilder contentSb = new StringBuilder();
        String line;
        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
        while ((line = br.readLine()) != null) {
          contentSb.append(line);
        }
        String str = contentSb.toString();
        inputStream.close();
        String[] strings = str.split("--"+this.fileUploadBoundary);
        String firstSplit = strings[1];
        int indexStart = firstSplit.indexOf("{\"filename\":");
        body = firstSplit.substring(indexStart).trim();
      }else {
        body = EntityUtils.toString(((HttpEntityEnclosingRequestBase) request).getEntity());
      }
    }
    return request.getRequestLine().getMethod() + "\n"
      + canonicalUrl + "\n"
      + timestamp + "\n"
      + nonce + "\n"
      + body + "\n";
  }

}

