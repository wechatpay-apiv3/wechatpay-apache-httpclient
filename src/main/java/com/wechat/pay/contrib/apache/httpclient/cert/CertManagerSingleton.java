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
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
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

    private volatile static CertManagerSingleton instance = null;
    /**
     * 证书下载地址
     */
    private final String CERT_DOWNLOAD_PATH = "https://api.mch.weixin.qq.com/v3/certificates";
    private final String SCHEDULE_UPDATE_CERT_THREAD_NAME = "schedule_update_cert_thread";
    /**
     * 执行定时更新证书的线程池
     */
    private final ScheduledExecutorService executor = new SafeSingleScheduleExecutor();
    /**
     * 在线程池的执行队列中允许的最多任务数量
     */
    private final byte[] apiV3Key;
    /**
     * 证书 map
     */
    private final HashMap<BigInteger, X509Certificate> certificates = new HashMap<>();
    private final Credentials credentials;

    private CertManagerSingleton(Credentials credentials, byte[] apiV3Key, long minutesInterval) {
        this.credentials = credentials;
        this.apiV3Key = apiV3Key;

        // 初始化证书
        initCertificates();

        // 启动定时更新证书任务
        executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.currentThread().setName(SCHEDULE_UPDATE_CERT_THREAD_NAME);
                    log.info("Begin update Certificate.Date:{}", new Date());
                    updateCertificates();
                    log.info("Finish update Certificate.Date:{}", new Date());
                } catch (Throwable t) {
                    log.error("Update Certificate failed", t);
                }
            }
        }, 0, minutesInterval, TimeUnit.MINUTES);
    }

    /**
     * 获取平台证书管理器实例
     *
     * @return
     */
    public static CertManagerSingleton getInstance() {
        if (instance == null) {
            throw new NullPointerException("请先调用 init 方法初初始化实例");
        }
        return instance;
    }

    /**
     * 初始化平台证书管理器实例
     *
     * @param credentials
     * @param apiV3Key
     * @param minutesInterval
     */
    public static void init(Credentials credentials, byte[] apiV3Key, long minutesInterval) {
        if (instance == null) {
            synchronized (CertManagerSingleton.class) {
                if (instance == null) {
                    instance = new CertManagerSingleton(credentials, apiV3Key, minutesInterval);
                }
            }
        }
    }

    /**
     * 关闭定时更新平台证书功能
     */
    public void stopScheduleUpdate() {
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
    public synchronized void downloadAndUpdateCert(Verifier verifier) {
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
    public void initCertificates() {
        downloadAndUpdateCert(null);
    }

    /**
     * 更新平台证书 map
     */
    public void updateCertificates() {
        Verifier verifier = null;
        if (!this.certificates.isEmpty()) {
            verifier = new CertificatesVerifier(this.certificates);
        }
        downloadAndUpdateCert(verifier);
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
