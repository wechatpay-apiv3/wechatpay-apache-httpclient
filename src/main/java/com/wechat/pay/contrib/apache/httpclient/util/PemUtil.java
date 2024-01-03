package com.wechat.pay.contrib.apache.httpclient.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * @author xy-peng
 */
public class PemUtil {

    public static PrivateKey loadPrivateKey(String privateKey) {
        privateKey = privateKey
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");

        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey)));

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("当前Java环境不支持RSA", e);
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException("无效的密钥格式");
        }
    }

    public static PrivateKey loadPrivateKey(InputStream inputStream) {
        if(inputStream == null){
            throw new IllegalArgumentException("无效文件流");
        }
        ByteArrayOutputStream os = new ByteArrayOutputStream(2048);
        byte[] buffer = new byte[1024];
        String privateKey;
        try {
            for (int length; (length = inputStream.read(buffer)) != -1; ) {
                os.write(buffer, 0, length);
            }
            privateKey = os.toString("UTF-8");
        } catch (IOException e) {
            throw new IllegalArgumentException("无效的密钥", e);
        }finally {
            try {
                inputStream.close();
                os.close();
            } catch (IOException e) {
                // ignore
            }
        }
        return loadPrivateKey(privateKey);
    }

    public static X509Certificate loadCertificate(InputStream inputStream) {
        if(inputStream == null){
            throw new IllegalArgumentException("无效文件流");
        }
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(inputStream);
            cert.checkValidity();
            return cert;
        } catch (CertificateExpiredException e) {
            throw new RuntimeException("证书已过期", e);
        } catch (CertificateNotYetValidException e) {
            throw new RuntimeException("证书尚未生效", e);
        } catch (CertificateException e) {
            throw new RuntimeException("无效的证书", e);
        }finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

}
