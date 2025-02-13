package com.wechat.pay.contrib.apache.httpclient.notification;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.wechat.pay.contrib.apache.httpclient.auth.Verifier;
import com.wechat.pay.contrib.apache.httpclient.exception.ParseException;
import com.wechat.pay.contrib.apache.httpclient.exception.ValidationException;
import com.wechat.pay.contrib.apache.httpclient.notification.Notification.Resource;
import com.wechat.pay.contrib.apache.httpclient.util.AesUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

/**
 * @author lianup
 */
public class NotificationHandler {

    private final Verifier verifier;
    private final byte[] apiV3Key;
    private static final ObjectMapper objectMapper = new ObjectMapper();

public NotificationHandler(Verifier verifier, byte[] apiV3Key) {
    if (verifier == null) {
        throw new IllegalArgumentException("verifier为空");
    }
    if (apiV3Key == null || apiV3Key.length == 0) {
        throw new IllegalArgumentException("apiV3Key为空");
    }
    this.verifier = verifier;
    this.apiV3Key = apiV3Key;
}

    /**
     * 解析微信支付通知请求结果
     *
     * @param request 微信支付通知请求
     * @return 微信支付通知报文解密结果
     * @throws ValidationException 1.输入参数不合法 2.参数被篡改导致验签失败 3.请求和验证的平台证书不一致导致验签失败
     * @throws ParseException 1.解析请求体为Json失败 2.请求体无对应参数 3.AES解密失败
     */
    public Notification parse(Request request)
            throws ValidationException, ParseException {
        // 验签
        validate(request);
        // 解析请求体
        return parseBody(request.getBody());
    }

    private void validate(Request request) throws ValidationException {
        if (request == null) {
            throw new ValidationException("request为空");
        }
        String serialNumber = request.getSerialNumber();
        byte[] message = request.getMessage();
        String signature = request.getSignature();
        if (serialNumber == null || serialNumber.isEmpty()) {
            throw new ValidationException("serialNumber为空");
        }
        if (message == null || message.length == 0) {
            throw new ValidationException("message为空");
        }
        if (signature == null || signature.isEmpty()) {
            throw new ValidationException("signature为空");
        }
        if (!verifier.verify(serialNumber, message, signature)) {
            String errorMessage = String
                    .format("验签失败：serial=[%s] message=[%s] sign=[%s]", serialNumber, new String(message), signature);
            throw new ValidationException(errorMessage);
        }
    }

    /**
     * 解析请求体
     *
     * @param body 请求体
     * @return 解析结果
     * @throws ParseException 解析body失败
     */
    public Notification parseBody(String body) throws ParseException {
        ObjectReader objectReader = objectMapper.reader();
        Notification notification;
        try {
            notification = objectReader.readValue(body, Notification.class);
        } catch (IOException ioException) {
            throw new ParseException("解析body失败，body:" + body, ioException);
        }
        validateNotification(notification);
        setDecryptData(notification);
        return notification;
    }

    /**
     * 校验解析后的通知结果
     *
     * @param notification 通知结果
     * @throws ParseException 参数不合法
     */
    public void validateNotification(Notification notification) throws ParseException {
        if (notification == null) {
            throw new ParseException("body解析为空");
        }
        String id = notification.getId();
        if (id == null || id.isEmpty()) {
            throw new ParseException("body不合法，id为空。body：" + notification.toString());
        }
        String createTime = notification.getCreateTime();
        if (createTime == null || createTime.isEmpty()) {
            throw new ParseException("body不合法，createTime为空。body：" + notification.toString());
        }
        String eventType = notification.getEventType();
        if (eventType == null || eventType.isEmpty()) {
            throw new ParseException("body不合法，eventType为空。body：" + notification.toString());
        }
        String summary = notification.getSummary();
        if (summary == null || summary.isEmpty()) {
            throw new ParseException("body不合法，summary为空。body：" + notification.toString());
        }
        String resourceType = notification.getResourceType();
        if (resourceType == null || resourceType.isEmpty()) {
            throw new ParseException("body不合法，resourceType为空。body：" + notification.toString());
        }
        Resource resource = notification.getResource();
        if (resource == null) {
            throw new ParseException("body不合法，resource为空。notification：" + notification.toString());
        }
        String algorithm = resource.getAlgorithm();
        if (algorithm == null || algorithm.isEmpty()) {
            throw new ParseException("body不合法，algorithm为空。body：" + notification.toString());
        }
        String originalType = resource.getOriginalType();
        if (originalType == null || originalType.isEmpty()) {
            throw new ParseException("body不合法，original_type为空。body：" + notification.toString());
        }
        String ciphertext = resource.getCiphertext();
        if (ciphertext == null || ciphertext.isEmpty()) {
            throw new ParseException("body不合法，ciphertext为空。body：" + notification.toString());
        }
        String nonce = resource.getNonce();
        if (nonce == null || nonce.isEmpty()) {
            throw new ParseException("body不合法，nonce为空。body：" + notification.toString());
        }
    }

    /**
     * 获取解密数据
     *
     * @param notification 解析body得到的通知结果
     * @throws ParseException 解析body失败
     */
    public void setDecryptData(Notification notification) throws ParseException {

        Resource resource = notification.getResource();
        String getAssociateddData = "";
        if (resource.getAssociatedData() != null) {
            getAssociateddData = resource.getAssociatedData();
        }
        byte[] associatedData = getAssociateddData.getBytes(StandardCharsets.UTF_8);
        byte[] nonce = resource.getNonce().getBytes(StandardCharsets.UTF_8);
        String ciphertext = resource.getCiphertext();
        AesUtil aesUtil = new AesUtil(apiV3Key);
        String decryptData;
        try {
            decryptData = aesUtil.decryptToString(associatedData, nonce, ciphertext);
        } catch (GeneralSecurityException e) {
            throw new ParseException("AES解密失败，resource：" + resource.toString(), e);
        }
        notification.setDecryptData(decryptData);
    }

}
