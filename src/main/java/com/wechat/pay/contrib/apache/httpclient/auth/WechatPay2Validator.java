package com.wechat.pay.contrib.apache.httpclient.auth;

import com.wechat.pay.contrib.apache.httpclient.Validator;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WechatPay2Validator implements Validator {

  private static final Logger log = LoggerFactory.getLogger(WechatPay2Validator.class);

  private Verifier verifier;

  public WechatPay2Validator(Verifier verifier) {
    this.verifier = verifier;
  }

  static RuntimeException parameterError(String message, Object... args) {
    message = String.format(message, args);
    return new IllegalArgumentException("parameter error: " + message);
  }

  static RuntimeException verifyFail(String message, Object... args) {
    message = String.format(message, args);
    return new IllegalArgumentException("signature verify fail: " + message);
  }

  @Override
  public final boolean validate(CloseableHttpResponse response) throws IOException {
    try {
      validateParameters(response);

      String message = buildMessage(response);
      String serial = response.getFirstHeader("Wechatpay-Serial").getValue();
      String signature = response.getFirstHeader("Wechatpay-Signature").getValue();

      if (!verifier.verify(serial, message.getBytes("utf-8"), signature)) {
        throw verifyFail("serial=[%s] message=[%s] sign=[%s], request-id=[%s]",
            serial, message, signature,
            response.getFirstHeader("Request-ID").getValue());
      }
    } catch (IllegalArgumentException e) {
      log.warn(e.getMessage());
      return false;
    }

    return true;
  }

  protected final void validateParameters(CloseableHttpResponse response) {
    String requestId;
    if (!response.containsHeader("Request-ID")) {
      throw parameterError("empty Request-ID");
    } else {
      requestId = response.getFirstHeader("Request-ID").getValue();
    }

    if (!response.containsHeader("Wechatpay-Serial")) {
      throw parameterError("empty Wechatpay-Serial, request-id=[%s]", requestId);
    } else if (!response.containsHeader("Wechatpay-Signature")){
      throw parameterError("empty Wechatpay-Signature, request-id=[%s]", requestId);
    } else if (!response.containsHeader("Wechatpay-Timestamp")) {
      throw parameterError("empty Wechatpay-Timestamp, request-id=[%s]", requestId);
    } else if (!response.containsHeader("Wechatpay-Nonce")) {
      throw parameterError("empty Wechatpay-Nonce, request-id=[%s]", requestId);
    } else {
      Header timestamp = response.getFirstHeader("Wechatpay-Timestamp");
      try {
        Instant instant = Instant.ofEpochSecond(Long.parseLong(timestamp.getValue()));
        // 拒绝5分钟之外的应答
        if (Duration.between(instant, Instant.now()).abs().toMinutes() >= 5) {
          throw parameterError("timestamp=[%s] expires, request-id=[%s]",
              timestamp.getValue(), requestId);
        }
      } catch (DateTimeException | NumberFormatException e) {
        throw parameterError("invalid timestamp=[%s], request-id=[%s]",
            timestamp.getValue(), requestId);
      }
    }
  }

  protected final String buildMessage(CloseableHttpResponse response) throws IOException {
    String timestamp = response.getFirstHeader("Wechatpay-Timestamp").getValue();
    String nonce = response.getFirstHeader("Wechatpay-Nonce").getValue();

    String body = getResponseBody(response);
    return timestamp + "\n"
          + nonce + "\n"
          + body + "\n";
  }

  protected final String getResponseBody(CloseableHttpResponse response) throws IOException {
    HttpEntity entity = response.getEntity();

    return (entity != null && entity.isRepeatable()) ? EntityUtils.toString(entity) : "";
  }
}
