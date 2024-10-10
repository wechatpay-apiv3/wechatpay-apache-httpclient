package com.wechat.pay.contrib.apache.httpclient.cert;

import static org.apache.http.HttpHeaders.ACCEPT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;

import com.wechat.pay.contrib.apache.httpclient.Credentials;
import com.wechat.pay.contrib.apache.httpclient.WechatPayHttpClientBuilder;
import com.wechat.pay.contrib.apache.httpclient.auth.Verifier;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Validator;
import com.wechat.pay.contrib.apache.httpclient.exception.HttpCodeException;
import com.wechat.pay.contrib.apache.httpclient.exception.NotFoundException;
import com.wechat.pay.contrib.apache.httpclient.proxy.HttpProxyFactory;
import com.wechat.pay.contrib.apache.httpclient.util.CertSerializeUtil;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 平台证书管理器，定时更新证书（默认值为UPDATE_INTERVAL_MINUTE）
 *
 * @author lianup
 * @since 0.3.0
 */
public class CertificatesManager {

    protected static final int UPDATE_INTERVAL_MINUTE = 1440;
    private static final Logger log = LoggerFactory.getLogger(CertificatesManager.class);
    /**
     * 证书下载地址
     */
    private static final String CERT_DOWNLOAD_PATH = "https://api.mch.weixin.qq.com/v3/certificates";
    private static final String SCHEDULE_UPDATE_CERT_THREAD_NAME = "scheduled_update_cert_thread";
    private volatile static CertificatesManager instance = null;
    private ConcurrentHashMap<String, byte[]> apiV3Keys = new ConcurrentHashMap<>();

    private HttpProxyFactory proxyFactory;
    private HttpHost proxy;

    private ConcurrentHashMap<String, ConcurrentHashMap<BigInteger, X509Certificate>> certificates =
            new ConcurrentHashMap<>();

    private ConcurrentHashMap<String, Credentials> credentialsMap = new ConcurrentHashMap<>();
    /**
     * 执行定时更新平台证书的线程池
     */
    private ScheduledExecutorService executor;

    private CertificatesManager() {
    }

    public static CertificatesManager getInstance() {
        if (instance == null) {
            synchronized (CertificatesManager.class) {
                if (instance == null) {
                    instance = new CertificatesManager();
                }
            }
        }
        return instance;
    }

    /**
     * 增加需要自动更新平台证书的商户信息
     *
     * @param merchantId 商户号
     * @param credentials 认证器
     * @param apiV3Key APIv3密钥
     * @throws IOException IO错误
     * @throws GeneralSecurityException 通用安全错误
     * @throws HttpCodeException HttpCode错误
     */
    public synchronized void putMerchant(String merchantId, Credentials credentials, byte[] apiV3Key)
            throws IOException, GeneralSecurityException, HttpCodeException {
        if (merchantId == null || merchantId.isEmpty()) {
            throw new IllegalArgumentException("merchantId为空");
        }
        if (credentials == null) {
            throw new IllegalArgumentException("credentials为空");
        }
        if (apiV3Key.length == 0) {
            throw new IllegalArgumentException("apiV3Key为空");
        }
        // 添加或更新商户信息
        if (certificates.get(merchantId) == null) {
            certificates.put(merchantId, new ConcurrentHashMap<>());
        }
        initCertificates(merchantId, credentials, apiV3Key);
        credentialsMap.put(merchantId, credentials);
        apiV3Keys.put(merchantId, apiV3Key);
        // 若没有executor，则启动定时更新证书任务
        if (executor == null) {
            beginScheduleUpdate();
        }
    }

    /***
     * 代理配置
     *
     * @param proxy 代理host
     **/
    public synchronized void setProxy(HttpHost proxy) {
        this.proxy = proxy;
    }

    /**
     * 设置代理工厂
     *
     * @param proxyFactory 代理工厂
     */
    public synchronized void setProxyFactory(HttpProxyFactory proxyFactory) {
        this.proxyFactory = proxyFactory;
    }

    public synchronized HttpHost resolveProxy() {
        return Objects.nonNull(proxyFactory) ? proxyFactory.buildHttpProxy() : proxy;
    }

    /**
     * 停止自动更新平台证书，停止后无法再重新启动
     */
    public void stop() {
        if (executor != null) {
            try {
                executor.shutdownNow();
            } catch (Exception e) {
                log.error("Executor shutdown now failed", e);
            }
        }
    }

    private X509Certificate getLatestCertificate(String merchantId)
            throws NotFoundException {
        if (merchantId == null || merchantId.isEmpty()) {
            throw new IllegalArgumentException("merchantId为空");
        }
        Map<BigInteger, X509Certificate> merchantCertificates = certificates.get(merchantId);
        if (merchantCertificates == null || merchantCertificates.isEmpty()) {
            throw new NotFoundException("没有最新的平台证书，merchantId:" + merchantId);
        }
        X509Certificate latestCert = null;
        for (X509Certificate x509Cert : merchantCertificates.values()) {
            // 若latestCert为空或x509Cert的证书有效开始时间在latestCert之后，则更新latestCert
            if (latestCert == null || x509Cert.getNotBefore().after(latestCert.getNotBefore())) {
                latestCert = x509Cert;
            }
        }
        try {
            latestCert.checkValidity();
            return latestCert;
        } catch (CertificateExpiredException | CertificateNotYetValidException e) {
            log.error("平台证书未生效或已过期，merchantId:{}", merchantId);
        }
        throw new NotFoundException("没有最新的平台证书，merchantId:" + merchantId);
    }

    /**
     * 获取商户号为merchantId的验签器
     *
     * @param merchantId 商户号
     * @return 验签器
     * @throws NotFoundException merchantId/merchantCertificates/apiV3Key/credentials为空
     */
    public Verifier getVerifier(String merchantId) throws NotFoundException {
        // 若商户信息不存在，返回错误
        ConcurrentHashMap<BigInteger, X509Certificate> merchantCertificates = certificates.get(merchantId);
        byte[] apiV3Key = apiV3Keys.get(merchantId);
        Credentials credentials = credentialsMap.get(merchantId);
        if (merchantId == null || merchantId.isEmpty()) {
            throw new IllegalArgumentException("merchantId为空");
        }
        if (merchantCertificates == null || merchantCertificates.size() == 0) {
            throw new NotFoundException("平台证书为空，merchantId:" + merchantId);
        }
        if (apiV3Key.length == 0) {
            throw new NotFoundException("apiV3Key为空，merchantId:" + merchantId);

        }
        if (credentials == null) {
            throw new NotFoundException("credentials为空，merchantId:" + merchantId);
        }
        return new DefaultVerifier(merchantId);
    }

    private void beginScheduleUpdate() {
        executor = new SafeSingleScheduleExecutor();
        Runnable runnable = () -> {
            try {
                Thread.currentThread().setName(SCHEDULE_UPDATE_CERT_THREAD_NAME);
                log.info("Begin update Certificates.Date:{}", Instant.now());
                updateCertificates();
                log.info("Finish update Certificates.Date:{}", Instant.now());
            } catch (Throwable t) {
                log.error("Update Certificates failed", t);
            }
        };
        executor.scheduleAtFixedRate(runnable, 0, UPDATE_INTERVAL_MINUTE, TimeUnit.MINUTES);
    }

    /**
     * 下载和更新平台证书
     *
     * @param merchantId 商户号
     * @param verifier 验签器
     * @param credentials 认证器
     * @param apiV3Key apiv3密钥
     * @throws HttpCodeException Http返回码异常
     * @throws IOException IO异常
     * @throws GeneralSecurityException 通用安全性异常
     */
    private synchronized void downloadAndUpdateCert(String merchantId, Verifier verifier, Credentials credentials,
            byte[] apiV3Key) throws HttpCodeException, IOException, GeneralSecurityException {
        proxy = resolveProxy();
        try (CloseableHttpClient httpClient = WechatPayHttpClientBuilder.create()
                .withCredentials(credentials)
                .withValidator(verifier == null ? (response) -> true
                        : new WechatPay2Validator(verifier))
                .withProxy(proxy)
                .build()) {
            HttpGet httpGet = new HttpGet(CERT_DOWNLOAD_PATH);
            httpGet.addHeader(ACCEPT, APPLICATION_JSON.toString());
            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(response.getEntity());
                if (statusCode == SC_OK) {
                    Map<BigInteger, X509Certificate> newCertList = CertSerializeUtil.deserializeToCerts(apiV3Key, body);
                    if (newCertList.isEmpty()) {
                        log.warn("Cert list is empty");
                        return;
                    }
                    ConcurrentHashMap<BigInteger, X509Certificate> merchantCertificates = certificates.get(merchantId);
                    merchantCertificates.clear();
                    merchantCertificates.putAll(newCertList);
                } else {
                    log.error("Auto update cert failed, statusCode = {}, body = {}", statusCode, body);
                    throw new HttpCodeException("下载平台证书返回状态码异常，状态码为:" + statusCode);
                }
            }
        }
    }

    /**
     * 初始化平台证书，商户信息第一次被添加时调用
     *
     * @param merchantId 商户号
     * @param credentials 认证器
     * @param apiV3Key apiv3密钥
     * @throws HttpCodeException Http返回码异常
     * @throws IOException IO异常
     * @throws GeneralSecurityException 通用安全性异常
     */
    private void initCertificates(String merchantId, Credentials credentials, byte[] apiV3Key)
            throws HttpCodeException, IOException, GeneralSecurityException {
        downloadAndUpdateCert(merchantId, null, credentials, apiV3Key);
    }

    /**
     * 更新平台证书，每UPDATE_INTERVAL_MINUTE调用一次
     */
    private void updateCertificates() {
        for (Map.Entry<String, Credentials> entry : credentialsMap.entrySet()) {
            String merchantId = entry.getKey();
            Credentials credentials = entry.getValue();
            byte[] apiv3Key = apiV3Keys.get(merchantId);
            Verifier verifier = new DefaultVerifier(merchantId);
            try {
                downloadAndUpdateCert(merchantId, verifier, credentials, apiv3Key);
            } catch (Exception e) {
                log.error("downloadAndUpdateCert Failed.merchantId:{}, e:{}", merchantId, e);
            }
        }
    }

    /**
     * 内部验签器
     */
    private class DefaultVerifier implements Verifier {

        private String merchantId;

        private DefaultVerifier(String merchantId) {
            this.merchantId = merchantId;
        }

        @Override
        public boolean verify(String serialNumber, byte[] message, String signature) {
            if (serialNumber.isEmpty() || message.length == 0 || signature.isEmpty()) {
                throw new IllegalArgumentException("serialNumber或message或signature为空");
            }
            BigInteger serialNumber16Radix = new BigInteger(serialNumber, 16);
            ConcurrentHashMap<BigInteger, X509Certificate> merchantCertificates = certificates.get(merchantId);
            X509Certificate certificate = merchantCertificates.get(serialNumber16Radix);
            if (certificate == null) {
                log.error("商户证书为空，serialNumber:{}", serialNumber);
                return false;
            }
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
        public X509Certificate getValidCertificate() {
            X509Certificate certificate;
            try {
                certificate = CertificatesManager.this.getLatestCertificate(merchantId);
            } catch (NotFoundException e) {
                throw new NoSuchElementException("没有有效的微信支付平台证书");
            }
            return certificate;
        }
    }
}
