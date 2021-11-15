package com.wechat.pay.contrib.apache.httpclient;

import static org.apache.http.HttpHeaders.ACCEPT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wechat.pay.contrib.apache.httpclient.auth.CertificatesVerifier;
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CertManagerSingleton {

    protected static final Logger log = LoggerFactory.getLogger(CertManagerSingleton.class);

    private volatile static CertManagerSingleton instance = null;
    protected final Credentials credentials;
    /**
     * 证书 map
     */
    protected final HashMap<BigInteger, X509Certificate> certificates = new HashMap<>();
    /**
     * 证书下载地址
     */
    protected final String CERT_DOWNLOAD_PATH = "https://api.mch.weixin.qq.com/v3/certificates";
    protected final byte[] apiV3Key;
    /**
     * 执行定时更新证书的线程池
     */
    protected final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private CertManagerSingleton(Credentials credentials, byte[] apiV3Key, long minutesInterval) {
        this.credentials = credentials;
        this.apiV3Key = apiV3Key;
        // 下载并更新证书
        updateCertWithoutVerifier();
        // 启动定时更新证书任务
        executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                updateCertWithVerifier();
            }
        }, 0, minutesInterval, TimeUnit.MINUTES);
    }

    /**
     * 获取平台证书管理器实例
     *
     * @return
     */
    public static CertManagerSingleton getInstance(Credentials credentials, byte[] apiV3Key, long minutesInterval) {
        if (instance == null) {
            synchronized (CertManagerSingleton.class) {
                if (instance == null) {
                    instance = new CertManagerSingleton(credentials, apiV3Key, minutesInterval);
                }
            }
        }
        return instance;
    }

    /**
     * 获取平台证书 map
     */
    public Map<BigInteger, X509Certificate> getCertificates() {
        return this.certificates;
    }

    /**
     * 获取最新的证书
     *
     * @return
     */
    public X509Certificate getLatestCertificate() {
        X509Certificate latestCert = null;
        for (X509Certificate x509Cert : this.certificates.values()) {
            if (latestCert == null || x509Cert.getNotBefore().after(latestCert.getNotBefore())) {
                latestCert = x509Cert;
            }
        }
        return latestCert;
    }

    /**
     * 下载和更新证书
     *
     * @param verifier
     */
    public synchronized void downloadAndUpdateCert(Verifier verifier) {
        try (CloseableHttpClient httpClient = WechatPayHttpClientBuilder.create()
                .withCredentials(credentials)
                .withValidator(verifier == null ? (response) -> true
                        : new WechatPay2Validator(verifier))
                .build()) {
            HttpGet httpGet = new HttpGet(CERT_DOWNLOAD_PATH);
            httpGet.addHeader(ACCEPT, APPLICATION_JSON.toString());
            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(response.getEntity());
                if (statusCode == SC_OK) {
                    Map<BigInteger, X509Certificate> newCertList = deserializeToCerts(apiV3Key, body);
                    if (newCertList.isEmpty()) {
                        log.warn("Cert list is empty");
                        return;
                    }
                    this.certificates.clear();
                    this.certificates.putAll(newCertList);
                } else {
                    log.warn("Auto update cert failed, statusCode = {}, body = {}", statusCode, body);
                }
            }
        } catch (IOException | GeneralSecurityException e) {
            e.printStackTrace();
        }
    }

    public void updateCertWithoutVerifier() {
        downloadAndUpdateCert(null);
    }

    public void updateCertWithVerifier() {
        Verifier verifier = null;
        if (!this.certificates.isEmpty()) {
            verifier = new CertificatesVerifier(this.certificates);
        }
        downloadAndUpdateCert(verifier);
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
    protected Map<BigInteger, X509Certificate> deserializeToCerts(byte[] apiV3Key, String body)
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

    /**
     * 获取 serialNumber 对应的证书
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
