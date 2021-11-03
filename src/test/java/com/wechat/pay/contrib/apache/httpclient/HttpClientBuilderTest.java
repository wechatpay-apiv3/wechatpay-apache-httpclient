package com.wechat.pay.contrib.apache.httpclient;

import static org.apache.http.HttpHeaders.ACCEPT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.wechat.pay.contrib.apache.httpclient.util.PemUtil;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.function.Consumer;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HttpClientBuilderTest {

    private static final String mchId = "1900009191"; // 商户号
    private static final String mchSerialNo = "1DDE55AD98ED71D6EDD4A4A16996DE7B47773A8C"; // 商户证书序列号
    private static final String requestBody = "{\n"
            + "    \"stock_id\": \"9433645\",\n"
            + "    \"stock_creator_mchid\": \"1900006511\",\n"
            + "    \"out_request_no\": \"20190522_001中文11\",\n"
            + "    \"appid\": \"wxab8acb865bb1637e\"\n"
            + "}";
    // 你的商户私钥
    private static final String privateKey = "-----BEGIN PRIVATE KEY-----\n"
            + "-----END PRIVATE KEY-----";
    // 你的微信支付平台证书
    private static final String certificate = "-----BEGIN CERTIFICATE-----\n"
            + "-----END CERTIFICATE-----";
    private CloseableHttpClient httpClient;

    @Before
    public void setup() {
        PrivateKey merchantPrivateKey = PemUtil.loadPrivateKey(privateKey);
        X509Certificate wechatPayCertificate = PemUtil.loadCertificate(
                new ByteArrayInputStream(certificate.getBytes(StandardCharsets.UTF_8)));

        ArrayList<X509Certificate> listCertificates = new ArrayList<>();
        listCertificates.add(wechatPayCertificate);

        httpClient = WechatPayHttpClientBuilder.create()
                .withMerchant(mchId, mchSerialNo, merchantPrivateKey)
                .withWechatPay(listCertificates)
                .build();
    }

    @After
    public void after() throws IOException {
        httpClient.close();
    }

    @Test
    public void getCertificateTest() throws Exception {
        URIBuilder uriBuilder = new URIBuilder("https://api.mch.weixin.qq.com/v3/certificates");
        uriBuilder.setParameter("p", "1&2");
        uriBuilder.setParameter("q", "你好");

        HttpGet httpGet = new HttpGet(uriBuilder.build());

        doSend(httpGet, null, response -> assertEquals(SC_OK, response.getStatusLine().getStatusCode()));

    }

    @Test
    public void getCertificatesWithoutCertTest() throws Exception {
        PrivateKey merchantPrivateKey = PemUtil.loadPrivateKey(privateKey);

        httpClient = WechatPayHttpClientBuilder.create()
                .withMerchant(mchId, mchSerialNo, merchantPrivateKey)
                .withValidator(response -> true)
                .build();

        getCertificateTest();
    }

    @Test
    public void postNonRepeatableEntityTest() throws IOException {
        HttpPost httpPost = new HttpPost(
                "https://api.mch.weixin.qq.com/v3/marketing/favor/users/oHkLxt_htg84TUEbzvlMwQzVDBqo/coupons");

        final byte[] bytes = requestBody.getBytes(StandardCharsets.UTF_8);
        final InputStream stream = new ByteArrayInputStream(bytes);
        doSend(httpPost, new InputStreamEntity(stream, bytes.length, APPLICATION_JSON),
                response -> assertTrue(response.getStatusLine().getStatusCode() != SC_UNAUTHORIZED));
    }

    @Test
    public void postRepeatableEntityTest() throws IOException {
        HttpPost httpPost = new HttpPost(
                "https://api.mch.weixin.qq.com/v3/marketing/favor/users/oHkLxt_htg84TUEbzvlMwQzVDBqo/coupons");

        doSend(httpPost, new StringEntity(requestBody, APPLICATION_JSON),
                response -> assertTrue(response.getStatusLine().getStatusCode() != SC_UNAUTHORIZED));
    }

    protected void doSend(HttpUriRequest request, HttpEntity entity, Consumer<CloseableHttpResponse> responseCallback)
            throws IOException {
        if (entity != null && request instanceof HttpPost) {
            ((HttpPost) request).setEntity(entity);
        }
        request.addHeader(ACCEPT, APPLICATION_JSON.toString());

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            responseCallback.accept(response);
        }
    }

}
