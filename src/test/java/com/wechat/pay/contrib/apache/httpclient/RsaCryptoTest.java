package com.wechat.pay.contrib.apache.httpclient;

import static com.wechat.pay.contrib.apache.httpclient.constant.WechatPayHttpHeaders.WECHAT_PAY_SERIAL;
import static org.apache.http.HttpHeaders.ACCEPT;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.wechat.pay.contrib.apache.httpclient.auth.PrivateKeySigner;
import com.wechat.pay.contrib.apache.httpclient.auth.Verifier;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Credentials;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Validator;
import com.wechat.pay.contrib.apache.httpclient.cert.CertificatesManager;
import com.wechat.pay.contrib.apache.httpclient.util.PemUtil;
import com.wechat.pay.contrib.apache.httpclient.util.RsaCryptoUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.management.PersistentMBean;

public class RsaCryptoTest {

    private static final String mchId = ""; // 商户号
    private static final String mchSerialNo = ""; // 商户证书序列号
    private static final String apiV3Key = ""; // API V3密钥
    private static final String privateKey = ""; // 商户API V3私钥
    private static final String wechatPaySerial = ""; // 平台证书序列号

    private static final String certForEncrypt = "-----BEGIN CERTIFICATE-----\n" +
            "MIIC4jCCAcoCCQCtzUA6NgI3njANBgkqhkiG9w0BAQsFADAzMQswCQYDVQQGEwJD\n" +
            "TjERMA8GA1UECAwIc2hhbmdoYWkxETAPBgNVBAcMCHNoYW5naGFpMB4XDTIyMDUw\n" +
            "OTIwMjE1NloXDTIzMDUwOTIwMjE1NlowMzELMAkGA1UEBhMCQ04xETAPBgNVBAgM\n" +
            "CHNoYW5naGFpMREwDwYDVQQHDAhzaGFuZ2hhaTCCASIwDQYJKoZIhvcNAQEBBQAD\n" +
            "ggEPADCCAQoCggEBALMGZq4BnKaX/VXeg9rLkpE7LqQ5uxgIfKMKSvLzCHA3ZfOR\n" +
            "p9fl8DtD0/svTUJ0JNv/pFRjfNEmlzqSmAW922yBc4uGkDdqrgHmt4/fqsOXcdLt\n" +
            "foL5txTdgYutq/127HOhxwixAlJA0PHk6QMuLmG4GN+dwQHWAtQROufgupXoPe6y\n" +
            "B+w4y3GaCLXIoqgHJQDePFy4sYkNAeSlHFvomPz4RAivPemEiTh2AmJ+RTZa3qT7\n" +
            "8ZzJNqIM0UKHgcPSsMGTzchC7sV9WIDbQZseflz2ZDJIepJeGq/4TSIXBcyd1yUY\n" +
            "GWfQRb/l640C3Izj3nililXWFLCWW5dKBnUGqdMCAwEAATANBgkqhkiG9w0BAQsF\n" +
            "AAOCAQEAo4LkShFg+btEjQUxuShD7SQeNh2DDvdCtEQo5IUY7wtgm95fDGgR1QTA\n" +
            "9IElN0EpiyvHnPlsjisl8heCL/OnTvrvxJyOp64AiPO6l9j7/nbf9cMHXPOaZODa\n" +
            "hS4rdokqUAswRA7wkiK6+hOPw/90+P7EPw6xCNRYTfl2ii5jirisrkc6iOW2nbUd\n" +
            "MjFd3gRGBM/ks3oltGbQbTOwntrAb7wy5EYakdZoKix6CQlqZIdbDXJBEgdXPftt\n" +
            "80ReqYWTWYyffHCuALMzmFw0fd6gFb/md2oIb13tcKCwiAe1mQmnudRsDH5b5Zps\n" +
            "iSuewmex8WO7a4/lc2WWKpSb/8JwNQ==\n" +
            "-----END CERTIFICATE-----"; // 用于测试加密功能的证书
    private static final String privateKeyForDecrypt = "-----BEGIN PRIVATE KEY-----\n" +
            "MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQCzBmauAZyml/1V\n" +
            "3oPay5KROy6kObsYCHyjCkry8whwN2XzkafX5fA7Q9P7L01CdCTb/6RUY3zRJpc6\n" +
            "kpgFvdtsgXOLhpA3aq4B5reP36rDl3HS7X6C+bcU3YGLrav9duxzoccIsQJSQNDx\n" +
            "5OkDLi5huBjfncEB1gLUETrn4LqV6D3usgfsOMtxmgi1yKKoByUA3jxcuLGJDQHk\n" +
            "pRxb6Jj8+EQIrz3phIk4dgJifkU2Wt6k+/GcyTaiDNFCh4HD0rDBk83IQu7FfViA\n" +
            "20GbHn5c9mQySHqSXhqv+E0iFwXMndclGBln0EW/5euNAtyM4954pYpV1hSwlluX\n" +
            "SgZ1BqnTAgMBAAECggEAUjhnYhVFb9GwPQbEAfGq796BblVBUylarLqmb2wk/PzE\n" +
            "axgDQQnOyjk9m0g/MH0NDKkdPNCwW5JgtDrtbP2kT/IoMfVsOLdbEW538bDkyY29\n" +
            "bgU7LEYpyoBs5cyuh+tdb0HmmlxJV6ODEwVx6s8D6EdXzSOzp/c1N1Zuel5g80V/\n" +
            "oE5pTb6XBObrCq4ZmMT5y59pSroZDV+RlYZqYtXeCdni+9jzVb+7AM50wqp3D17M\n" +
            "P9OZnYVyiKS9GEM68klXt3dCnp5P80WVLLupin3DODGdkU0kDFWZE+Hw8Xype5iP\n" +
            "jgJMZwieOsniveAsIjwtRjh0yZ6xJe47G1JOGppK8QKBgQDuM5eyIJhhwkxbQ1ov\n" +
            "PbRQbeHrdWUuWuruggg2BMXI3RQH5yELyysOY7rrxSob0L90ww7QLtvaw4GNAFsV\n" +
            "/OpXs1bkn3QD3lCh4jskbe816iHnYpLKcdkewcIove3wVAaT5/VYeyW9R1mXZLFr\n" +
            "sXAYef1Fys1yg6eM8GuiFGu7yQKBgQDAZtue4T1JNR4IEMXU4wRUmU2itu+7A2W6\n" +
            "GskyKaXNvKt0g8ZawDIYEl+B35mRJ29O+8rGKIpqqMEfy+En9/aphouMu9S0cFfS\n" +
            "n/H1M1B9cfscqyXnS/Ed1kCC9SlfkRXuJ+HhZQ7Zt95vHwf2ugYeg6GDtghC4JHA\n" +
            "NIdntlOOuwKBgQCi8IvN/1n9VUmiDBp+wji7485soGtMIEkgSbaQLQeWdRQkq8gB\n" +
            "J0MWnsXYTZCWYl704hEZ+1PM+3t9Fkc4bT9oKndAAIr9sm95rSVDsCe3u6bhfp5m\n" +
            "+SXKUkQcVn+SrAer2ToNAoA4T7xLQUfUIRZKx/embCnJMaHFWRhnUIy5cQKBgQCG\n" +
            "tHz3E8OQybuo8fVQQ1D42gxc66+UQ6CpV6+di0Mmc/2mqcvqJb3s1JBBoYcm9XEc\n" +
            "33Tsn92pJ1VvKZMOJLFxp110vt0BJ9aVBJ6mibLE4VRqkfkLo0PBHAw2o+a/nhi4\n" +
            "kPu4jsSC8hStwBAXUc6O9qHSUVQfXpMs+poCpsiBmQKBgQDO+B/xX6V6GQIrPgiF\n" +
            "nKpSi566ouXcNxiMIb8w7nu4r/0mJ91roVD0N1TyCOVKTrY/R/4KsQV4pp2bQfV7\n" +
            "3tYnrSVgBhPHfWkWQG+7sUXWRR5/c8jszKM+no/bsxmqsAJdK2ih/crHD7XrGgXL\n" +
            "XWU1WCYDnWGKm3byXlLY1tFO/Q==\n" +
            "-----END PRIVATE KEY-----"; // 用于测试解密功能的私钥

    private CloseableHttpClient httpClient;
    private CertificatesManager certificatesManager;
    private Verifier verifier;

    @Before
    public void setup() throws Exception {
        PrivateKey merchantPrivateKey = PemUtil.loadPrivateKey(privateKey);
        // 获取证书管理器实例
        certificatesManager = CertificatesManager.getInstance();
        // 向证书管理器增加需要自动更新平台证书的商户信息
        certificatesManager.putMerchant(mchId, new WechatPay2Credentials(mchId,
                new PrivateKeySigner(mchSerialNo, merchantPrivateKey)), apiV3Key.getBytes(StandardCharsets.UTF_8));
        // 从证书管理器中获取verifier
        verifier = certificatesManager.getVerifier(mchId);
        httpClient = WechatPayHttpClientBuilder.create()
                .withMerchant(mchId, mchSerialNo, merchantPrivateKey)
                .withValidator(new WechatPay2Validator(certificatesManager.getVerifier(mchId)))
                .build();
    }

    @After
    public void after() throws IOException {
        httpClient.close();
    }

    @Test
    public void encryptTest() throws Exception {
        String text = "helloworld";
        String ciphertext = RsaCryptoUtil
                .encryptOAEP(text, verifier.getValidCertificate());
        System.out.println("ciphertext: " + ciphertext);
    }

    @Test
    public void encryptWithPkcs1TransformationTest() throws IllegalBlockSizeException {
        String text = "helloworld";
        String transformation = "RSA/ECB/PKCS1Padding";
        String encryptedText = RsaCryptoUtil.encrypt(text, PemUtil.loadCertificate(new ByteArrayInputStream(certForEncrypt.getBytes())), transformation);
        System.out.println("encrypted text: " + encryptedText);
    }

    @Test
    public void decryptWithPkcs1TransformationTest() throws BadPaddingException {
        String encryptedText = "lmkkdBz5CH4Zk6KIEzbyenf+WtKe8nuU9j+t8HonOm4v1OfLRiYhvdcequOSuaz5vjdpX434XjV9Q5LGC8aOC" +
                "DZs/8LoyR3m/6JpYa0nkGOh6Le2JvSPNXlSq9HUEoElBJD5KsxbsRoif0kuioBGSKvKB0xwIvVtn+S0H2GYya7TC1L/ddhGhI/yx" +
                "ZgS/TI/Ppej3OzNmu0xA5RjpDR4rGAUrLvV7y/aM4mCN6WOaO6YsAnlGoSbK+P1sepeb0sCaJMClqbLE0Eoz2ve9FQ30w1Vgi5F0" +
                "2rpDwcZO8EXAkub0L12BN4QWBNK8FaKlS4UZPAGAwutLK6Gylig54Quig==";
        String transformation = "RSA/ECB/PKCS1Padding";
        assertEquals("helloworld", RsaCryptoUtil.decrypt(encryptedText, PemUtil.loadPrivateKey(privateKeyForDecrypt), transformation));
    }

    @Test
    public void encryptWithOaepTransformationTest() throws IllegalBlockSizeException {
        String text = "helloworld";
        String transformation = "RSA/ECB/OAEPWithSHA-1AndMGF1Padding";
        String encryptedText = RsaCryptoUtil.encrypt(text, PemUtil.loadCertificate(new ByteArrayInputStream(certForEncrypt.getBytes())), transformation);
        System.out.println("encrypted text: " + encryptedText);
    }

    @Test
    public void decryptWithOaepTransformationTest() throws BadPaddingException {
        String encryptedText = "FJ8/0ubyxnMZ0GN2YEUgJgDVPCwMrsTKuLFxycI3jvOAcVTDEEermn2F7+cUtmCYvD2TkHUMHvWeJB6/nSPBD" +
                "eGuxA4bCr584h2w9bRvVrwtQlnv1HpF2WRdGAuPcgrQcZvMpiH2ysxgPrGPMs9WOr8etxf1FifI0DkMb6w7wl2BDPPK+RfRdZq7T" +
                "9KBtH2IllVTLUbRSqDGIctgIxB7RMqd3s/eK0p2Qjui8AVgP4j5Spq6JjITgKn0VDOO4JwzU8Zl++BwveoJMkTN150XF5ot+ruZv" +
                "lNgjP1Hez0/rFxY7gQvxrSDwgL5A9up6JRI741psfs/3HrzBJOBdvO73A==";
        String transformation = "RSA/ECB/OAEPWithSHA-1AndMGF1Padding";
        assertEquals("helloworld", RsaCryptoUtil.decrypt(encryptedText, PemUtil.loadPrivateKey(privateKeyForDecrypt), transformation));
    }

    @Test
    public void postEncryptDataTest() throws Exception {
        HttpPost httpPost = new HttpPost("https://api.mch.weixin.qq.com/v3/smartguide/guides");

        String text = "helloworld";
        String ciphertext = RsaCryptoUtil
                .encryptOAEP(text, verifier.getValidCertificate());

        String data = "{\n"
                + "  \"store_id\" : 1234,\n"
                + "  \"corpid\" : \"1234567890\",\n"
                + "  \"name\" : \"" + ciphertext + "\",\n"
                + "  \"mobile\" : \"" + ciphertext + "\",\n"
                + "  \"qr_code\" : \"https://open.work.weixin.qq.com/wwopen/userQRCode?vcode=xxx\",\n"
                + "  \"sub_mchid\" : \"1234567890\",\n"
                + "  \"avatar\" : \"logo\",\n"
                + "  \"userid\" : \"robert\"\n"
                + "}";
        StringEntity reqEntity = new StringEntity(data, APPLICATION_JSON);
        httpPost.setEntity(reqEntity);
        httpPost.addHeader(ACCEPT, APPLICATION_JSON.toString());
        httpPost.addHeader(WECHAT_PAY_SERIAL, wechatPaySerial);

        CloseableHttpResponse response = httpClient.execute(httpPost);
        assertTrue(response.getStatusLine().getStatusCode() != SC_UNAUTHORIZED);
        assertTrue(response.getStatusLine().getStatusCode() != SC_BAD_REQUEST);
        try {
            HttpEntity entity = response.getEntity();
            // do something useful with the response body
            // and ensure it is fully consumed
            EntityUtils.consume(entity);
        } finally {
            response.close();
        }
    }
}
