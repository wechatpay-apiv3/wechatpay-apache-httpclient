package com.wechat.pay.contrib.apache.httpclient;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.execchain.ClientExecChain;
import com.wechat.pay.contrib.apache.httpclient.auth.CertificatesVerifier;
import com.wechat.pay.contrib.apache.httpclient.auth.PrivateKeySigner;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Credentials;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Validator;

public class WechatPayHttpClientBuilder extends HttpClientBuilder {

  private Credentials credentials;
  private Validator validator;

  static final String os = System.getProperty("os.name") + "/" + System.getProperty("os.version");
  static final String version = System.getProperty("java.version");

  private WechatPayHttpClientBuilder() {
    super();

    String userAgent = String.format(
        "WechatPay-Apache-HttpClient/%s (%s) Java/%s",
        getClass().getPackage().getImplementationVersion(),
        os,
        version == null ? "Unknown" : version);
    setUserAgent(userAgent);
  }

  public static WechatPayHttpClientBuilder create() {
    return new WechatPayHttpClientBuilder();
  }

  public WechatPayHttpClientBuilder withMerchant(String merchantId, String serialNo, PrivateKey privateKey) {
    this.credentials = new WechatPay2Credentials(merchantId, new PrivateKeySigner(serialNo, privateKey));
    return this;
  }

  public WechatPayHttpClientBuilder withCredentials(Credentials credentials) {
    this.credentials = credentials;
    return this;
  }

  public WechatPayHttpClientBuilder withWechatPay(List<X509Certificate> certificates) {
    // TODO 这个方法的命名不能修改，否则会存在向后版本兼容性问题
    this.validator = new WechatPay2Validator(new CertificatesVerifier(certificates));
    return this;
  }

  public WechatPayHttpClientBuilder withValidator(Validator validator) {
    this.validator = validator;
    return this;
  }

  @Override
  public CloseableHttpClient build() {
    if (credentials == null) {
      throw new IllegalArgumentException("缺少身份认证信息");
    }
    if (validator == null) {
      throw new IllegalArgumentException("缺少签名验证信息");
    }
    return super.build();
  }

  @Override
  protected ClientExecChain decorateProtocolExec(final ClientExecChain requestExecutor) {
    return new SignatureExec(this.credentials, this.validator, requestExecutor);
  }

}