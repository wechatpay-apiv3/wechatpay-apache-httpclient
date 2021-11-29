package com.wechat.pay.contrib.apache.httpclient.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

/**
 * @author lianup
 * @since 0.3.0
 */
public class CertSerializeUtil {

    /**
     * 反序列化证书并解密
     *
     * @param apiV3Key APIv3密钥
     * @param body 下载证书的请求返回体
     * @return 证书list
     * @throws GeneralSecurityException 当证书过期或尚未生效时
     * @throws IOException 当body不合法时
     */
    public static Map<BigInteger, X509Certificate> deserializeToCerts(byte[] apiV3Key, String body)
            throws GeneralSecurityException, IOException {
        AesUtil aesUtil = new AesUtil(apiV3Key);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode dataNode = mapper.readTree(body).get("data");
        Map<BigInteger, X509Certificate> newCertList = new HashMap<>();
        if (dataNode != null) {
            for (int i = 0, count = dataNode.size(); i < count; i++) {
                JsonNode node = dataNode.get(i).get("encrypt_certificate");
                //解密
                String cert = aesUtil.decryptToString(
                        node.get("associated_data").toString().replace("\"", "")
                                .getBytes(StandardCharsets.UTF_8),
                        node.get("nonce").toString().replace("\"", "")
                                .getBytes(StandardCharsets.UTF_8),
                        node.get("ciphertext").toString().replace("\"", ""));

                CertificateFactory cf = CertificateFactory.getInstance("X509");
                X509Certificate x509Cert = (X509Certificate) cf.generateCertificate(
                        new ByteArrayInputStream(cert.getBytes(StandardCharsets.UTF_8))
                );
                try {
                    x509Cert.checkValidity();
                } catch (CertificateExpiredException | CertificateNotYetValidException ignored) {
                    continue;
                }
                newCertList.put(x509Cert.getSerialNumber(), x509Cert);
            }
        }
        return newCertList;
    }

}
