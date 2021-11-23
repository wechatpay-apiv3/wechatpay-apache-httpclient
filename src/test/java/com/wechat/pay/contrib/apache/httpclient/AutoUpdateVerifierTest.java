package com.wechat.pay.contrib.apache.httpclient;

import static org.apache.http.HttpHeaders.ACCEPT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.wechat.pay.contrib.apache.httpclient.auth.AutoUpdateCertificatesVerifier;
import com.wechat.pay.contrib.apache.httpclient.auth.PrivateKeySigner;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Credentials;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Validator;
import com.wechat.pay.contrib.apache.httpclient.util.PemUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@Deprecated
public class AutoUpdateVerifierTest {

    // 你的商户私钥
    private static final String privateKey = "-----BEGIN PRIVATE KEY-----\n"
            + "-----END PRIVATE KEY-----\n";
    //测试AutoUpdateCertificatesVerifier的verify方法参数
    private static final String serialNumber = "";
    private static final String message = "";
    private static final String signature = "";
    private static final String mchId = ""; // 商户号
    private static final String mchSerialNo = ""; // 商户证书序列号
    private static final String apiV3Key = ""; // API V3密钥
    private CloseableHttpClient httpClient;
    private AutoUpdateCertificatesVerifier verifier;

    @Before
    public void setup() {
        PrivateKey merchantPrivateKey = PemUtil.loadPrivateKey(privateKey);

        //使用自动更新的签名验证器，不需要传入证书
        verifier = new AutoUpdateCertificatesVerifier(
                new WechatPay2Credentials(mchId, new PrivateKeySigner(mchSerialNo, merchantPrivateKey)),
                apiV3Key.getBytes(StandardCharsets.UTF_8));

        httpClient = WechatPayHttpClientBuilder.create()
                .withMerchant(mchId, mchSerialNo, merchantPrivateKey)
                .withValidator(new WechatPay2Validator(verifier))
                .build();
    }

    @After
    public void after() throws IOException {
        httpClient.close();
    }

    @Test
    public void autoUpdateVerifierTest() {
        assertTrue(verifier.verify(serialNumber, message.getBytes(StandardCharsets.UTF_8), signature));
    }

    @Test
    public void getCertificateTest() throws Exception {
        URIBuilder uriBuilder = new URIBuilder("https://api.mch.weixin.qq.com/v3/certificates");
        HttpGet httpGet = new HttpGet(uriBuilder.build());
        httpGet.addHeader(ACCEPT, APPLICATION_JSON.toString());
        CloseableHttpResponse response = httpClient.execute(httpGet);
        assertEquals(SC_OK, response.getStatusLine().getStatusCode());
        try {
            HttpEntity entity = response.getEntity();
            // do something useful with the response body
            // and ensure it is fully consumed
            EntityUtils.consume(entity);
        } finally {
            response.close();
        }
    }

    @Test
    public void uploadImageTest() throws Exception {
        String filePath = "/your/home/hellokitty.png";

        URI uri = new URI("https://api.mch.weixin.qq.com/v3/merchant/media/upload");

        File file = new File(filePath);
        try (FileInputStream fileIs = new FileInputStream(file)) {
            String sha256 = DigestUtils.sha256Hex(fileIs);
            try (InputStream is = new FileInputStream(file)) {
                WechatPayUploadHttpPost request = new WechatPayUploadHttpPost.Builder(uri)
                        .withImage(file.getName(), sha256, is)
                        .build();

                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    assertEquals(SC_OK, response.getStatusLine().getStatusCode());
                    HttpEntity entity = response.getEntity();
                    // do something useful with the response body
                    // and ensure it is fully consumed
                    String s = EntityUtils.toString(entity);
                    System.out.println(s);
                }
            }
        }
    }
}
