package com.wechat.pay.contrib.apache.httpclient.auth;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PublicKey;
import java.util.Objects;


/**
 * MixVerifier 混合Verifier，仅用于切换平台证书与微信支付公钥时提供兼容
 *
 * 本实例需要使用一个 PublicKeyVerifier + 一个 Verifier 初始化，前者提供微信支付公钥验签，后者提供平台证书验签
 */
public class MixVerifier implements Verifier {
    private static final Logger log = LoggerFactory.getLogger(MixVerifier.class);

    final PublicKeyVerifier publicKeyVerifier;
    final Verifier certificateVerifier;

    public MixVerifier(PublicKeyVerifier publicKeyVerifier, Verifier certificateVerifier) {
        if (publicKeyVerifier == null) {
            throw new IllegalArgumentException("publicKeyVerifier cannot be null");
        }

        this.publicKeyVerifier = publicKeyVerifier;
        this.certificateVerifier = certificateVerifier;
    }

    @Override
    public boolean verify(String serialNumber, byte[] message, String signature) {
        if (Objects.equals(publicKeyVerifier.getSerialNumber(), serialNumber)) {
            return publicKeyVerifier.verify(serialNumber, message, signature);
        }

        if (certificateVerifier != null) {
            return certificateVerifier.verify(serialNumber, message, signature);
        }

        log.error("找不到证书序列号对应的证书，序列号：{}", serialNumber);
        return false;
    }

    @Override
    public PublicKey getValidPublicKey() {
        return publicKeyVerifier.getValidPublicKey();
    }

    @Override
    public String getSerialNumber() {
        return publicKeyVerifier.getSerialNumber();
    }
}
