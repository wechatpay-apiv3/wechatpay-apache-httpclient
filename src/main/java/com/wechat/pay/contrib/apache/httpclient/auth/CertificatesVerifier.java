package com.wechat.pay.contrib.apache.httpclient.auth;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * @author xy-peng
 */
public class CertificatesVerifier implements Verifier {

    protected final HashMap<BigInteger, X509Certificate> certificates = new HashMap<>();

    public CertificatesVerifier(List<X509Certificate> list) {
        for (X509Certificate item : list) {
            certificates.put(item.getSerialNumber(), item);
        }
    }

    public CertificatesVerifier(Map<BigInteger, X509Certificate> certificates) {
        this.certificates.putAll(certificates);
    }


    public void updateCertificates(Map<BigInteger, X509Certificate> certificates) {
        this.certificates.clear();
        this.certificates.putAll(certificates);
    }

    protected boolean verify(X509Certificate certificate, byte[] message, String signature) {
        try {
            Signature sign = Signature.getInstance("SHA256withRSA");
            sign.initVerify(certificate);
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
    public boolean verify(String serialNumber, byte[] message, String signature) {
        BigInteger val = new BigInteger(serialNumber, 16);
        X509Certificate cert = certificates.get(val);
        return cert != null && verify(cert, message, signature);
    }

    @Override
    public X509Certificate getValidCertificate() {
        for (X509Certificate x509Cert : certificates.values()) {
            try {
                x509Cert.checkValidity();
                return x509Cert;
            } catch (CertificateExpiredException | CertificateNotYetValidException ignored) {
            }
        }
        throw new NoSuchElementException("没有有效的微信支付平台证书");
    }

    @Override
    public X509Certificate getLatestCertificate() {
        X509Certificate latestCert = null;
        for (X509Certificate x509Cert : certificates.values()) {
            // 若latestCert为空或x509Cert的证书有效开始时间在latestCert之后，则更新latestCert
            if (latestCert == null || x509Cert.getNotBefore().after(latestCert.getNotBefore())) {
                latestCert = x509Cert;
            }
        }
        return latestCert;
    }
}

