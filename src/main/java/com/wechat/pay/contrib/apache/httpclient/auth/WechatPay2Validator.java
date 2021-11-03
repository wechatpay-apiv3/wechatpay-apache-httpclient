package com.wechat.pay.contrib.apache.httpclient.auth;


import static com.wechat.pay.contrib.apache.httpclient.constant.WechatPayHttpHeaders.REQUEST_ID;
import static com.wechat.pay.contrib.apache.httpclient.constant.WechatPayHttpHeaders.WECHAT_PAY_NONCE;
import static com.wechat.pay.contrib.apache.httpclient.constant.WechatPayHttpHeaders.WECHAT_PAY_SERIAL;
import static com.wechat.pay.contrib.apache.httpclient.constant.WechatPayHttpHeaders.WECHAT_PAY_SIGNATURE;
import static com.wechat.pay.contrib.apache.httpclient.constant.WechatPayHttpHeaders.WECHAT_PAY_TIMESTAMP;

import com.wechat.pay.contrib.apache.httpclient.Validator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author xy-peng
 */
public class WechatPay2Validator implements Validator {

    protected static final Logger log = LoggerFactory.getLogger(WechatPay2Validator.class);
    /**
     * 应答超时时间，单位为分钟
     */
    protected static final long RESPONSE_EXPIRED_MINUTES = 5;
    protected final Verifier verifier;

    public WechatPay2Validator(Verifier verifier) {
        this.verifier = verifier;
    }

    protected static IllegalArgumentException parameterError(String message, Object... args) {
        message = String.format(message, args);
        return new IllegalArgumentException("parameter error: " + message);
    }

    protected static IllegalArgumentException verifyFail(String message, Object... args) {
        message = String.format(message, args);
        return new IllegalArgumentException("signature verify fail: " + message);
    }

    @Override
    public final boolean validate(CloseableHttpResponse response) throws IOException {
        try {
            validateParameters(response);

            String message = buildMessage(response);
            String serial = response.getFirstHeader(WECHAT_PAY_SERIAL).getValue();
            String signature = response.getFirstHeader(WECHAT_PAY_SIGNATURE).getValue();

            if (!verifier.verify(serial, message.getBytes(StandardCharsets.UTF_8), signature)) {
                throw verifyFail("serial=[%s] message=[%s] sign=[%s], request-id=[%s]",
                        serial, message, signature, response.getFirstHeader(REQUEST_ID).getValue());
            }
        } catch (IllegalArgumentException e) {
            log.warn(e.getMessage());
            return false;
        }

        return true;
    }

    protected final void validateParameters(CloseableHttpResponse response) {
        Header firstHeader = response.getFirstHeader(REQUEST_ID);
        if (firstHeader == null) {
            throw parameterError("empty " + REQUEST_ID);
        }
        String requestId = firstHeader.getValue();

        // NOTE: ensure HEADER_WECHAT_PAY_TIMESTAMP at last
        String[] headers = {WECHAT_PAY_SERIAL, WECHAT_PAY_SIGNATURE, WECHAT_PAY_NONCE, WECHAT_PAY_TIMESTAMP};

        Header header = null;
        for (String headerName : headers) {
            header = response.getFirstHeader(headerName);
            if (header == null) {
                throw parameterError("empty [%s], request-id=[%s]", headerName, requestId);
            }
        }

        String timestampStr = header.getValue();
        try {
            Instant responseTime = Instant.ofEpochSecond(Long.parseLong(timestampStr));
            // 拒绝过期应答
            if (Duration.between(responseTime, Instant.now()).abs().toMinutes() >= RESPONSE_EXPIRED_MINUTES) {
                throw parameterError("timestamp=[%s] expires, request-id=[%s]", timestampStr, requestId);
            }
        } catch (DateTimeException | NumberFormatException e) {
            throw parameterError("invalid timestamp=[%s], request-id=[%s]", timestampStr, requestId);
        }
    }

    protected final String buildMessage(CloseableHttpResponse response) throws IOException {
        String timestamp = response.getFirstHeader(WECHAT_PAY_TIMESTAMP).getValue();
        String nonce = response.getFirstHeader(WECHAT_PAY_NONCE).getValue();
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
