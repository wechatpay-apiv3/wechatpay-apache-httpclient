package com.wechat.pay.contrib.apache.httpclient.cert;

import static org.apache.http.HttpHeaders.ACCEPT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;

import com.wechat.pay.contrib.apache.httpclient.Credentials;
import com.wechat.pay.contrib.apache.httpclient.WechatPayHttpClientBuilder;
import com.wechat.pay.contrib.apache.httpclient.auth.CertificatesVerifier;
import com.wechat.pay.contrib.apache.httpclient.auth.Verifier;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Validator;
import com.wechat.pay.contrib.apache.httpclient.util.SerializeUtil;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 平台证书管理器
 */
public class CertManagerSingleton {

    private static final Logger log = LoggerFactory.getLogger(CertManagerSingleton.class);
    /**
     * 证书下载地址
     */
    private static final String CERT_DOWNLOAD_PATH = "https://api.mch.weixin.qq.com/v3/certificates";
    private static final String SCHEDULE_UPDATE_CERT_THREAD_NAME = "schedule_update_cert_thread";
    private volatile static CertManagerSingleton instance = null;
    private byte[] apiV3Key;
    /**
     * 平台证书 map
     */
    private ConcurrentHashMap<BigInteger, X509Certificate> certificates;
    private Credentials credentials;
    /**
     * 执行定时更新平台证书的线程池
     */
    private ScheduledExecutorService executor;

    private CertManagerSingleton() {
    }

    /**
     * 获取平台证书管理器实例
     *
     * @return
     */
    public static CertManagerSingleton getInstance() {
        if (instance == null) {
            synchronized (CertManagerSingleton.class) {
                if (instance == null) {
                    instance = new CertManagerSingleton();
                }
            }
        }
        return instance;
    }

    /**
     * 初始化平台证书管理器实例，在使用前需先调用该方法
     *
     * @param credentials
     * @param apiV3Key
     * @param minutesInterval
     */
    public synchronized void init(Credentials credentials, byte[] apiV3Key, long minutesInterval) {
        if (this.credentials == null || this.apiV3Key.length == 0 || this.executor == null
                || this.certificates == null) {
            this.credentials = credentials;
            this.apiV3Key = apiV3Key;
            this.executor = new SafeSingleScheduleExecutor();
            this.certificates = new ConcurrentHashMap<>();

            // 初始化证书
            initCertificates();

            // 启动定时更新证书任务
            Runnable runnable = () -> {
                try {
                    Thread.currentThread().setName(SCHEDULE_UPDATE_CERT_THREAD_NAME);
                    log.info("Begin update Certificate.Date:{}", Instant.now());
                    updateCertificates();
                    log.info("Finish update Certificate.Date:{}", Instant.now());
                } catch (Throwable t) {
                    log.error("Update Certificate failed", t);
                }
            };
            executor.scheduleAtFixedRate(runnable, 0, minutesInterval, TimeUnit.MINUTES);
        }
    }

    /**
     * 判断平台证书管理器是否被初始化
     *
     * @return
     */
    public boolean isInit() {
        if (this.certificates == null || this.apiV3Key == null || this.executor == null) {
            return false;
        }
        return true;
    }

    /**
     * 关闭线程池
     */
    public void close() {
        if (this.executor == null) {
            throw new IllegalStateException("请先调用 init 方法初始化实例");
        }
        try {
            this.executor.shutdownNow();
        } catch (Exception e) {
            log.error("Executor shutdown now failed", e);
        }
    }

    /**
     * 获取平台证书 map
     */
    public Map<BigInteger, X509Certificate> getCertificates() {
        if (this.certificates == null) {
            throw new IllegalStateException("请先调用 init 方法初始化实例");
        }
        return this.certificates;
    }

    /**
     * 获取最新的证书
     *
     * @return
     */
    public X509Certificate getLatestCertificate() {
        if (this.certificates == null) {
            throw new IllegalStateException("请先调用 init 方法初始化实例");
        }
        X509Certificate latestCert = null;
        for (X509Certificate x509Cert : this.certificates.values()) {
            // 若 latestCert 为空或 x509Cert 的证书有效开始时间在 latestCert 之后，则更新 latestCert
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
    private synchronized void downloadAndUpdateCert(Verifier verifier) {
        try (CloseableHttpClient httpClient = WechatPayHttpClientBuilder.create()
                .withCredentials(this.credentials)
                .withValidator(verifier == null ? (response) -> true
                        : new WechatPay2Validator(verifier))
                .build()) {
            HttpGet httpGet = new HttpGet(CERT_DOWNLOAD_PATH);
            httpGet.addHeader(ACCEPT, APPLICATION_JSON.toString());
            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(response.getEntity());
                if (statusCode == SC_OK) {
                    Map<BigInteger, X509Certificate> newCertList = SerializeUtil.deserializeToCerts(apiV3Key, body);
                    if (newCertList.isEmpty()) {
                        log.warn("Cert list is empty");
                        return;
                    }
                    this.certificates.clear();
                    this.certificates.putAll(newCertList);
                } else {
                    log.error("Auto update cert failed, statusCode = {}, body = {}", statusCode, body);
                }
            }
        } catch (IOException | GeneralSecurityException e) {
            log.error("Download Certificate failed", e);
        }
    }

    /**
     * 初始化平台证书 map
     */
    private void initCertificates() {
        downloadAndUpdateCert(null);
    }

    /**
     * 更新平台证书 map
     */
    private void updateCertificates() {
        Verifier verifier = null;
        if (!this.certificates.isEmpty()) {
            verifier = new CertificatesVerifier(this.certificates);
        }
        downloadAndUpdateCert(verifier);
    }
}
