# wechatpay-apache-httpclient 

## 概览

[微信支付API v3](https://wechatpay-api.gitbook.io/wechatpay-api-v3/)的[Apache HttpClient](https://hc.apache.org/httpcomponents-client-ga/index.html)扩展，实现了请求签名的生成和应答签名的验证。

如果你是使用Apache HttpClient的商户开发者，可以使用它构造`HttpClient`。得到的`HttpClient`在执行请求时将自动携带身份认证信息，并检查应答的微信支付签名。

## 项目状态

当前版本`0.2.0`为测试版本。请商户的专业技术人员在使用时注意系统和软件的正确性和兼容性，以及带来的风险。

## 环境要求

+ Java 1.8+

## 安装

### Gradle

在你的`build.gradle`文件中加入如下的信息

```groovy
repositories {
    ...
    maven { url 'https://jitpack.io' }
}
...
dependencies {
    implementation 'com.github.wechatpay-apiv3:wechatpay-apache-httpclient:0.1.6'
    ...
}
```

### Maven

加入JitPack仓库

```xml
	<repositories>
		<repository>
		    <id>jitpack.io</id>
		    <url>https://jitpack.io</url>
		</repository>
	</repositories>
```

加入以下依赖

```xml
	<dependency>
	    <groupId>com.github.wechatpay-apiv3</groupId>
	    <artifactId>wechatpay-apache-httpclient</artifactId>
	    <version>0.2.0</version>
	</dependency>
```

## 开始

如果你使用的是`HttpClientBuilder`或者`HttpClients#custom()`来构造`HttpClient`，你可以直接替换为`WechatPayHttpClientBuilder`。我们提供相应的方法，可以方便的传入商户私钥和微信支付平台证书等信息。

```java
import com.wechat.pay.contrib.apache.httpclient.WechatPayHttpClientBuilder;

//...
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
import com.wechat.pay.contrib.apache.httpclient.WechatPayHttpClientBuilder;
import com.wechat.pay.contrib.apache.httpclient.Credentials;

// ...
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

## 自动更新证书功能（可选）

新版本`>=0.1.5`可使用 AutoUpdateCertificatesVerifier 类，该类于原 CertificatesVerifier 上增加证书的**超时自动更新**（默认与上次更新时间超过一小时后自动更新），并会在首次创建时，进行证书更新。

示例代码：

```java
//不需要传入微信支付证书，将会自动更新
AutoUpdateCertificatesVerifier verifier = new AutoUpdateCertificatesVerifier(
        new WechatPay2Credentials(merchantId, new PrivateKeySigner(merchantSerialNumber, merchantPrivateKey)),
        apiV3Key.getBytes("utf-8"));


WechatPayHttpClientBuilder builder = WechatPayHttpClientBuilder.create()
        .withMerchant(merchantId, merchantSerialNumber, merchantPrivateKey)
        .withValidator(new WechatPay2Validator(verifier))
// ... 接下来，你仍然可以通过builder设置各种参数，来配置你的HttpClient

// 通过WechatPayHttpClientBuilder构造的HttpClient，会自动的处理签名和验签，并进行证书自动更新
HttpClient httpClient = builder.build();

// 后面跟使用Apache HttpClient一样
HttpResponse response = httpClient.execute(...);
```
### 风险

因为不需要传入微信支付平台证书，AutoUpdateCertificatesVerifier 在首次更新证书时**不会验签**，也就无法确认应答身份，可能导致下载错误的证书。

但下载时会通过 **HTTPS**、**AES 对称加密**来保证证书安全，所以可以认为，在使用官方 JDK、且 APIv3 密钥不泄露的情况下，AutoUpdateCertificatesVerifier 是**安全**的。

## 敏感信息加解密

### 加密

使用` RsaCryptoUtil.encryptOAEP(String, X509Certificate)`进行公钥加密。示例代码如下。

```java
// 建议从Verifier中获得微信支付平台证书，或使用预先下载到本地的平台证书文件中
X509Certificate wechatpayCertificate = verifier.getValidCertificate();
try {
  String ciphertext = RsaCryptoUtil.encryptOAEP(text, wechatpayCertificate);
} catch (IllegalBlockSizeException e) {
  e.printStackTrace();
}
```

### 解密

使用`RsaCryptoUtil.decryptOAEP(String ciphertext, PrivateKey privateKey)`进行私钥解密。示例代码如下。

```java
// 使用商户私钥解密
try {
  String ciphertext = RsaCryptoUtil.decryptOAEP(text, merchantPrivateKey);
} catch (BadPaddingException e) {
  e.printStackTrace();
}
```

## 图片/视频上传

我们对上传的参数组装和签名逻辑进行了一定的封装，只需要以下几步：

1. 使用`WechatPayUploadHttpPost`构造一个上传的`HttpPost`，需设置待上传文件的文件名，SHA256摘要，文件的输入流。
2. 通过`WechatPayHttpClientBuilder`得到的`HttpClient`发送请求。

示例请参考下列代码。

```java
String filePath = "/your/home/hellokitty.png";
URI uri = new URI("https://api.mch.weixin.qq.com/v3/merchant/media/upload");
File file = new File(filePath);

try (FileInputStream ins1 = new FileInputStream(file)) { 
  String sha256 = DigestUtils.sha256Hex(ins1);
  try (InputStream ins2 = new FileInputStream(file)) {
    HttpPost request = new WechatPayUploadHttpPost.Builder(uri)
        .withImage(file.getName(), sha256, ins2)
        .build();
    CloseableHttpResponse response1 = httpClient.execute(request);
  }
}
```

[AutoUpdateVerifierTest.uploadImageTest](/src/test/java/com/wechat/pay/contrib/apache/httpclient/AutoUpdateVerifierTest.java#L86)是一个更完整的示例。

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

### 证书和回调解密需要的AesGcm解密在哪里？

请参考[AesUtil.Java](https://github.com/wechatpay-apiv3/wechatpay-apache-httpclient/blob/master/src/main/java/com/wechat/pay/contrib/apache/httpclient/util/AesUtil.java)。

### 我想使用以前的版本，要怎么办

可以在gradle中指定版本号。例如希望使用0.1.6版本，可以使用以下的方式。

```xml
	<dependency>
	    <groupId>com.github.wechatpay-apiv3</groupId>
	    <artifactId>wechatpay-apache-httpclient</artifactId>
	    <version>0.1.6</version>
	</dependency>
```

## 联系我们

如果你发现了**BUG**或者有任何疑问、建议，请通过issue进行反馈。

也欢迎访问我们的[开发者社区](https://developers.weixin.qq.com/community/pay)。

