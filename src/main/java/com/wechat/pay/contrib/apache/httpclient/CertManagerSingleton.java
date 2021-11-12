package com.wechat.pay.contrib.apache.httpclient;

import static org.apache.http.HttpHeaders.ACCEPT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wechat.pay.contrib.apache.httpclient.auth.Verifier;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Validator;
import com.wechat.pay.contrib.apache.httpclient.util.AesUtil;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

public enum CertManagerSingleton {
    INSTANCE;
    /**
     * 证书 map
     */
    private final ConcurrentHashMap<BigInteger, X509Certificate> certificates = new ConcurrentHashMap<>();
    /**
     * 执行定时更新证书的线程池
     */
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    /**
     * 证书下载地址
     */
    private final String CERT_DOWNLOAD_PATH = "https://api.mch.weixin.qq.com/v3/certificates";
    private final ReentrantLock lock = new ReentrantLock();
    private final byte[] apiV3Key;
    /**
     * 证书更新间隔时间（24小时），单位为分钟
     */
    private final long minutesInterval = 1440;
    /**
     * 上次更新时间
     */
    private volatile Instant lastUpdateTime;

    CertManagerSingleton(Credentials credentials, Verifier verifier, byte[] apiV3Key) {
        this.apiV3Key = apiV3Key;
        // 下载并更新证书
        try {
            downloadAndUpdateCert(credentials);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        }
        lastUpdateTime = Instant.now();
        // 启动定时更新任务
        executor.execute();
    }

    /**
     * 获取最新的平台证书
     *
     * @return
     */
    public X509Certificate getLatestCertificate() {
        X509Certificate latestCert = null;
        for (X509Certificate x509Cert : certificates.values()) {
            if (latestCert == null || x509Cert.getNotBefore().after(latestCert.getNotBefore())) {
                latestCert = x509Cert;
            }
        }
        return latestCert;
    }

    public void downloadAndUpdateCert(Credentials credentials, Verifier verifier, String serialNumber)
            throws IOException, GeneralSecurityException {
        try (CloseableHttpClient httpClient = WechatPayHttpClientBuilder.create()
                .withCredentials(credentials)
                .withValidator(getDownloadCertVerifier(verifier, serialNumber) == null ? (response) -> true
                        : new WechatPay2Validator(verifier))
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
                } else {
                    log.warn("Auto update cert failed, statusCode = {}, body = {}", statusCode, body);
                }
            }
        }
    }

    public void downloadAndUpdateCert(Credentials credentials)
            throws IOException, GeneralSecurityException {
        try (CloseableHttpClient httpClient = WechatPayHttpClientBuilder.create()
                .withCredentials(credentials)
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
                } else {
                    log.warn("Auto update cert failed, statusCode = {}, body = {}", statusCode, body);
                }
            }
        }
    }

    /**
     * 反序列化证书并解密
     *
     * @param apiV3Key
     * @param body
     * @return
     * @throws GeneralSecurityException
     * @throws IOException
     */
    private List<X509Certificate> deserializeToCerts(byte[] apiV3Key, String body)
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
                } catch (CertificateExpiredException | CertificateNotYetValidException ignored) {
                    continue;
                }
                newCertList.add(x509Cert);
            }
        }
        return newCertList;
    }

    private Verifier getDownloadCertVerifier(Verifier verifier, String serialNumber) {
        // verifier 为空或本地没有对应的平台证书
        if (verifier == null || getCertificate(serialNumber) == null) {
            return null;
        }
        return verifier;
    }

    /**
     * 获取 serialNumber 对应的平台证书
     *
     * @param serialNumber
     * @return
     */
    public X509Certificate getCertificate(String serialNumber) {
        for (X509Certificate x509Cert : this.certificates.values()) {
            if (x509Cert.getSerialNumber().toString().equals(serialNumber)) {
                return x509Cert;
            }
        }
        return null;
    }

}
