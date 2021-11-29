package com.wechat.pay.contrib.apache.httpclient.auth;

import static org.apache.http.HttpHeaders.ACCEPT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wechat.pay.contrib.apache.httpclient.Credentials;
import com.wechat.pay.contrib.apache.httpclient.WechatPayHttpClientBuilder;
import com.wechat.pay.contrib.apache.httpclient.util.AesUtil;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 在原有CertificatesVerifier基础上，增加自动更新证书功能
 * 该类已废弃，请使用ScheduledUpdateCertificatesVerifier
 *
 * @author xy-peng
 */
@Deprecated
public class AutoUpdateCertificatesVerifier implements Verifier {

    protected static final Logger log = LoggerFactory.getLogger(AutoUpdateCertificatesVerifier.class);
    /**
     * 证书下载地址
     */
    private static final String CERT_DOWNLOAD_PATH = "https://api.mch.weixin.qq.com/v3/certificates";
    /**
     * 证书更新间隔时间，单位为分钟
     */
    protected final long minutesInterval;
    protected final Credentials credentials;
    protected final byte[] apiV3Key;
    protected final ReentrantLock lock = new ReentrantLock();
    /**
     * 上次更新时间
     */
    protected volatile Instant lastUpdateTime;
    protected CertificatesVerifier verifier;

    public AutoUpdateCertificatesVerifier(Credentials credentials, byte[] apiV3Key) {
        this(credentials, apiV3Key, TimeUnit.HOURS.toMinutes(1));
    }

    public AutoUpdateCertificatesVerifier(Credentials credentials, byte[] apiV3Key, long minutesInterval) {
        this.credentials = credentials;
        this.apiV3Key = apiV3Key;
        this.minutesInterval = minutesInterval;
        //构造时更新证书
        try {
            autoUpdateCert();
            lastUpdateTime = Instant.now();
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean verify(String serialNumber, byte[] message, String signature) {
        if (lastUpdateTime == null
                || Duration.between(lastUpdateTime, Instant.now()).toMinutes() >= minutesInterval) {
            if (lock.tryLock()) {
                try {
                    autoUpdateCert();
                    //更新时间
                    lastUpdateTime = Instant.now();
                } catch (GeneralSecurityException | IOException e) {
                    log.warn("Auto update cert failed: ", e);
                } finally {
                    lock.unlock();
                }
            }
        }
        return verifier.verify(serialNumber, message, signature);
    }

    @Override
    public X509Certificate getValidCertificate() {
        return verifier.getValidCertificate();
    }

    @Override
    public X509Certificate getLatestCertificate() {
        return verifier.getLatestCertificate();
    }

    protected void autoUpdateCert() throws IOException, GeneralSecurityException {
        try (CloseableHttpClient httpClient = WechatPayHttpClientBuilder.create()
                .withCredentials(credentials)
                .withValidator(verifier == null ? (response) -> true : new WechatPay2Validator(verifier))
                .build()) {

            HttpGet httpGet = new HttpGet(CERT_DOWNLOAD_PATH);
            httpGet.addHeader(ACCEPT, APPLICATION_JSON.toString());

            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(response.getEntity());
                if (statusCode == SC_OK) {
                    List<X509Certificate> newCertList = deserializeToCerts(apiV3Key, body);
                    if (newCertList.isEmpty()) {
                        log.warn("Cert list is empty");
                        return;
                    }
                    this.verifier = new CertificatesVerifier(newCertList);
                } else {
                    log.warn("Auto update cert failed, statusCode = {}, body = {}", statusCode, body);
                }
            }
        }
    }

    protected List<X509Certificate> deserializeToCerts(byte[] apiV3Key, String body)
            throws GeneralSecurityException, IOException {
        AesUtil aesUtil = new AesUtil(apiV3Key);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode dataNode = mapper.readTree(body).get("data");
        List<X509Certificate> newCertList = new ArrayList<>();
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
                } catch (CertificateExpiredException | CertificateNotYetValidException e) {
                    continue;
                }
                newCertList.add(x509Cert);
            }
        }
        return newCertList;
    }

}
