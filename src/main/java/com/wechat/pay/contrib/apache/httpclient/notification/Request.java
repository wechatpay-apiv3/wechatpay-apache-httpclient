package com.wechat.pay.contrib.apache.httpclient.notification;

/**
 * 通知请求体，包含验签所需信息和报文体
 *
 * @author lianup
 */
public interface Request {

    /**
     * 获取请求头Wechatpay-Serial
     *
     * @return serialNumber
     */
    String getSerialNumber();

    /**
     * 获取验签串
     *
     * @return message
     */
    byte[] getMessage();

    /**
     * 获取请求头Wechatpay-Signature
     *
     * @return signature
     */
    String getSignature();

    /**
     * 获取请求体
     *
     * @return body
     */
    String getBody();
}
