package com.wechat.pay.contrib.apache.httpclient.auth;

import java.security.PublicKey;

/**
 * @author xy-peng
 */
public interface Verifier {
    /**
     * @param serialNumber 微信支付序列号（微信支付公钥ID 或 平台证书序列号）
     * @param message 验签的原文
     * @param signature 验签的签名
     * @return 验证是否通过
     */
    boolean verify(String serialNumber, byte[] message, String signature);

    /**
     * 获取合法的公钥，针对不同的验签模式有所区别
     * <ul>
     *   <li>如果是微信支付公钥验签：则为微信支付公钥</li>
     *   <li>如果是平台证书验签：则为平台证书内部的公钥</li>
     * </ul>
     * @return 合法公钥
     */
    PublicKey getValidPublicKey();


    /**
     * 获取微信支付序列号，针对不同的验签模式有所区别：
     * <ul>
     *   <li>如果是微信支付公钥验签：则为公钥ID，以 <code>PUB_KEY_ID_</code> 开头</li>
     *   <li>如果是平台证书验签：则为平台证书序列号</li>
     * </ul>
     * @return 微信支付序列号
     */
    String getSerialNumber();
}
