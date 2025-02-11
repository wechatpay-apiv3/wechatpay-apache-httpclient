package com.wechat.pay.contrib.apache.httpclient.auth;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Base64;

public class PublicKeyVerifier implements Verifier {
    protected final PublicKey publicKey;
    protected final String publicKeyId;

    public PublicKeyVerifier(String publicKeyId, PublicKey publicKey) {
        this.publicKey = publicKey;
        this.publicKeyId = publicKeyId;
    }

    @Override
    public boolean verify(String serialNumber, byte[] message, String signature) {
        try {
            Signature sign = Signature.getInstance("SHA256withRSA");
            sign.initVerify(publicKey);
            sign.update(message);
            return sign.verify(Base64.getDecoder().decode(signature));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("当前Java环境不支持SHA256withRSA", e);
        } catch (SignatureException e) {
            throw new RuntimeException("签名验证过程发生了错误", e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException("无效的证书", e);
        }
    }

    @Override
    public PublicKey getValidPublicKey() {
        return publicKey;
    }

    @Override
    public String getSerialNumber() {
        return publicKeyId;
    }
}
