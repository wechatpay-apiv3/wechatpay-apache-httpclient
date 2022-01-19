package com.wechat.pay.contrib.apache.httpclient.notification;

import java.nio.charset.StandardCharsets;

/**
 * @author lianup
 */
public class NotificationRequest implements Request {

    private final String serialNumber;
    private final String signature;
    private final byte[] message;
    private final String body;

    private NotificationRequest(String serialNumber, String signature, byte[] message, String body) {
        this.serialNumber = serialNumber;
        this.signature = signature;
        this.message = message;
        this.body = body;
    }

    @Override
    public String getSerialNumber() {
        return serialNumber;
    }

    @Override
    public byte[] getMessage() {
        return message;
    }

    @Override
    public String getSignature() {
        return signature;
    }

    @Override
    public String getBody() {
        return body;
    }

    public static class Builder {

        private String serialNumber;
        private String timestamp;
        private String nonce;
        private String signature;
        private String body;

        public Builder() {
        }

        public Builder withSerialNumber(String serialNumber) {
            this.serialNumber = serialNumber;
            return this;
        }

        public Builder withTimestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder withNonce(String nonce) {
            this.nonce = nonce;
            return this;
        }

        public Builder withSignature(String signature) {
            this.signature = signature;
            return this;
        }

        public Builder withBody(String body) {
            this.body = body;
            return this;
        }

        public NotificationRequest build() {
            byte[] message = buildMessage();
            return new NotificationRequest(serialNumber, signature, message, body);
        }

        private byte[] buildMessage() {
            String verifyMessage = timestamp + "\n" + nonce + "\n" + body + "\n";
            return verifyMessage.getBytes(StandardCharsets.UTF_8);
        }
    }
}
