# wechatpay-apache-httpclient

## 概览

[微信支付API v3](https://wechatpay-api.gitbook.io/wechatpay-api-v3/)的[Apache HttpClient](https://hc.apache.org/httpcomponents-client-ga/index.html)扩展，实现了请求签名的生成和应答签名的验证。

如果你是使用Apache HttpClient的商户开发者，可以使用它构造`HttpClient`。得到的`HttpClient`在执行请求时将自动携带身份认证信息，并检查应答的微信支付签名。

## 项目状态

当前版本为测试版，尚没经过严格的功能和兼容性测试。请商户的专业技术人员在使用时注意系统和软件的正确性和兼容性。由此带来的风险，由商户自行承担。

## 环境要求

我们开发和测试使用的环境如下。

+ Java 1.8
+ Apache HttpClient 4.5.8

根据所依赖的接口，Apache HttpClient的版本大于4.3.1即可以使用，建议使用4.4以上版本。

## 开始

如果你使用的是`HttpClientBuilder`或者`HttpClients#custom()`来构造`HttpClient`，你可以直接替换为`WechatPayHttpClientBuilder`。我们提供相应的方法，可以方便的传入商户私钥和微信支付平台证书等信息。

```java
WechatPayHttpClientBuilder builder = WechatPayHttpClientBuilder.create()
        .withMerchant(merchantId, merchantSerialNumber, merchantPrivateKey)
        .withWechatpay(wechatpayCertificates);
// ... 接下来，你仍然可以通过builder设置各种参数，来配置你的HttpClient

// 通过WechatPayHttpClientBuilder构造的HttpClient，会自动的处理签名和验签
HttpClient httpClient = builder.build();

// 后面跟使用Apache HttpClient一样
HttpResponse response = httpClient.execute(...);
```

## 定制

当默认的本地签名和验签方式不适合你的系统时，你可以通过实现`Signer`或者`Verifier`来定制签名和验签。比如，你的系统把商户私钥集中存储，业务系统需通过远程调用进行签名，你可以这样做。

```java
Credentials credentials = new WechatPay2Credentials(merchantId, new Signer() {
  @Override
  public Signer.SignatureResult sign(byte[] message) {
    // ... call your sign-RPC, then return sign & serial number
  }
});
WechatPayHttpClientBuilder builder = WechatPayHttpClientBuilder.create()
        .withCredentials(credentials)
        .withWechatpay(wechatpayCertificates);
```

## 常见问题

### 如何下载平台证书？

使用`WechatPayHttpClientBuilder`需要调用`withWechatpay`设置[微信支付平台证书](https://wechatpay-api.gitbook.io/wechatpay-api-v3/ren-zheng/zheng-shu#ping-tai-zheng-shu)，而平台证书又只能通过调用[获取平台证书接口](https://wechatpay-api.gitbook.io/wechatpay-api-v3/jie-kou-wen-dang/ping-tai-zheng-shu#huo-qu-ping-tai-zheng-shu-lie-biao)下载。为了解开"死循环"，你可以在第一次下载平台证书时，按照下述方法临时"跳过”应答签名的验证。

```java
CloseableHttpClient httpClient = WechatPayHttpClientBuilder.create()
  .withMerchant(mchId, mchSerialNo, merchantPrivateKey)
  .withValidator(response -> true) // NOTE: 设置一个空的应答签名验证器，**不要**用在业务请求
  .build();
```

**注意**：业务请求请使用标准的初始化流程，务必验证应答签名。

## 联系我们

如果你发现了**BUG**或者有任何疑问，请通过issue进行反馈。

也欢迎访问我们的[开发者社区](https://developers.weixin.qq.com/community/pay)。

