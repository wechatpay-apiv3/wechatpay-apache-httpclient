# wechatpay-apache-httpclient

## 概览

[微信支付API v3](https://wechatpay-api.gitbook.io/wechatpay-api-v3/)的[Apache HttpClient](https://hc.apache.org/httpcomponents-client-ga/index.html)扩展，实现了请求签名的生成和应答签名的验证。

如果你是使用Apache HttpClient的商户开发者，可以使用它构造`HttpClient`。得到的`HttpClient`在执行请求时将自动携带身份认证信息，并检查应答的微信支付签名。

## 项目状态

当前版本`0.4.4`为测试版本。请商户的专业技术人员在使用时注意系统和软件的正确性和兼容性，以及带来的风险。

## 升级指引

若你使用的版本为`0.3.0`，升级前请参考[升级指南](UPGRADING.md)。

## 环境要求

+ Java 1.8+

## 安装

最新版本已经在 [Maven Central](https://search.maven.org/artifact/com.github.wechatpay-apiv3/wechatpay-apache-httpclient) 发布。

### Gradle

在你的`build.gradle`文件中加入如下的依赖

```groovy
implementation 'com.github.wechatpay-apiv3:wechatpay-apache-httpclient:0.4.4'
```

### Maven
加入以下依赖

```xml
<dependency>
    <groupId>com.github.wechatpay-apiv3</groupId>
    <artifactId>wechatpay-apache-httpclient</artifactId>
    <version>0.4.4</version>
</dependency>
```

## 名词解释

+ 商户API证书，是用来证实商户身份的。证书中包含商户号、证书序列号、证书有效期等信息，由证书授权机构(Certificate Authority ，简称CA)签发，以防证书被伪造或篡改。如何获取请见[商户API证书](https://wechatpay-api.gitbook.io/wechatpay-api-v3/ren-zheng/zheng-shu#shang-hu-api-zheng-shu)。
+ 商户API私钥。商户申请商户API证书时，会生成商户私钥，并保存在本地证书文件夹的文件apiclient_key.pem中。注：不要把私钥文件暴露在公共场合，如上传到Github，写在客户端代码等。
+ 微信支付平台证书。平台证书是指由微信支付负责申请的，包含微信支付平台标识、公钥信息的证书。商户可以使用平台证书中的公钥进行应答签名的验证。获取平台证书需通过[获取平台证书列表](https://wechatpay-api.gitbook.io/wechatpay-api-v3/ren-zheng/zheng-shu#ping-tai-zheng-shu)接口下载。
+ 证书序列号。每个证书都有一个由CA颁发的唯一编号，即证书序列号。如何查看证书序列号请看[这里](https://wechatpay-api.gitbook.io/wechatpay-api-v3/chang-jian-wen-ti/zheng-shu-xiang-guan#ru-he-cha-kan-zheng-shu-xu-lie-hao)。
+ API v3密钥。为了保证安全性，微信支付在回调通知和平台证书下载接口中，对关键信息进行了AES-256-GCM加密。API v3密钥是加密时使用的对称密钥。商户可以在【商户平台】->【API安全】的页面设置该密钥。

## 开始

如果你使用的是`HttpClientBuilder`或者`HttpClients#custom()`来构造`HttpClient`，你可以直接替换为`WechatPayHttpClientBuilder`。
```java
import com.wechat.pay.contrib.apache.httpclient.WechatPayHttpClientBuilder;

//...
WechatPayHttpClientBuilder builder = WechatPayHttpClientBuilder.create()
        .withMerchant(merchantId, merchantSerialNumber, merchantPrivateKey)
        .withWechatPay(wechatpayCertificates);
// ... 接下来，你仍然可以通过builder设置各种参数，来配置你的HttpClient

// 通过WechatPayHttpClientBuilder构造的HttpClient，会自动的处理签名和验签
CloseableHttpClient httpClient = builder.build();

// 后面跟使用Apache HttpClient一样
ClosableHttpResponse response = httpClient.execute(...);
```

参数说明：

+ `merchantId`商户号。
+ `merchantSerialNumber`商户API证书的证书序列号。
+ `merchantPrivateKey`商户API私钥，如何加载商户API私钥请看[常见问题](#如何加载商户私钥)。
+ `wechatpayCertificates`微信支付平台证书。你也可以使用后面章节提到的“[定时更新平台证书功能](#定时更新平台证书功能)”，而不需要关心平台证书的来龙去脉。

### 示例：获取平台证书

你可以使用`WechatPayHttpClientBuilder`构造的`HttpClient`发送请求和应答了。

```java
URIBuilder uriBuilder = new URIBuilder("https://api.mch.weixin.qq.com/v3/certificates");
HttpGet httpGet = new HttpGet(uriBuilder.build());
httpGet.addHeader("Accept", "application/json");

CloseableHttpResponse response = httpClient.execute(httpGet);

String bodyAsString = EntityUtils.toString(response.getEntity());
System.out.println(bodyAsString);
```

### 示例：JSAPI下单

注：

+ 我们使用了 jackson-databind 演示拼装 Json，你也可以使用自己熟悉的 Json 库
+ 请使用你自己的测试商户号、appid 以及对应的 openid

```java
HttpPost httpPost = new HttpPost("https://api.mch.weixin.qq.com/v3/pay/transactions/jsapi");
httpPost.addHeader("Accept", "application/json");
httpPost.addHeader("Content-type","application/json; charset=utf-8");

ByteArrayOutputStream bos = new ByteArrayOutputStream();
ObjectMapper objectMapper = new ObjectMapper();

ObjectNode rootNode = objectMapper.createObjectNode();
rootNode.put("mchid","1900009191")
        .put("appid", "wxd678efh567hg6787")
        .put("description", "Image形象店-深圳腾大-QQ公仔")
        .put("notify_url", "https://www.weixin.qq.com/wxpay/pay.php")
        .put("out_trade_no", "1217752501201407033233368018");
rootNode.putObject("amount")
        .put("total", 1);
rootNode.putObject("payer")
        .put("openid", "oUpF8uMuAJO_M2pxb1Q9zNjWeS6o");

objectMapper.writeValue(bos, rootNode);

httpPost.setEntity(new StringEntity(bos.toString("UTF-8"), "UTF-8"));
CloseableHttpResponse response = httpClient.execute(httpPost);

String bodyAsString = EntityUtils.toString(response.getEntity());
System.out.println(bodyAsString);
```

### 示例：查单

```java
URIBuilder uriBuilder = new URIBuilder("https://api.mch.weixin.qq.com/v3/pay/transactions/id/4200000889202103303311396384?mchid=1230000109");
HttpGet httpGet = new HttpGet(uriBuilder.build());
httpGet.addHeader("Accept", "application/json");

CloseableHttpResponse response = httpClient.execute(httpGet);

String bodyAsString = EntityUtils.toString(response.getEntity());
System.out.println(bodyAsString);
```

### 示例：关单

```java
HttpPost httpPost = new HttpPost("https://api.mch.weixin.qq.com/v3/pay/transactions/out-trade-no/1217752501201407033233368018/close");
httpPost.addHeader("Accept", "application/json");
httpPost.addHeader("Content-type","application/json; charset=utf-8");

ByteArrayOutputStream bos = new ByteArrayOutputStream();
ObjectMapper objectMapper = new ObjectMapper();

ObjectNode rootNode = objectMapper.createObjectNode();
rootNode.put("mchid","1900009191");

objectMapper.writeValue(bos, rootNode);

httpPost.setEntity(new StringEntity(bos.toString("UTF-8"), "UTF-8"));
CloseableHttpResponse response = httpClient.execute(httpPost);

String bodyAsString = EntityUtils.toString(response.getEntity());
System.out.println(bodyAsString);
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
        .withWechatPay(wechatpayCertificates);
```

## 定时更新平台证书功能

版本>=`0.4.0`可使用 CertificatesManager.getVerifier(mchId) 得到的验签器替代默认的验签器。它会定时下载和更新商户对应的[微信支付平台证书](https://wechatpay-api.gitbook.io/wechatpay-api-v3/ren-zheng/zheng-shu#ping-tai-zheng-shu) （默认下载间隔为UPDATE_INTERVAL_MINUTE）。

示例代码：
```java
// 获取证书管理器实例
certificatesManager = CertificatesManager.getInstance();
// 向证书管理器增加需要自动更新平台证书的商户信息
certificatesManager.putMerchant(mchId, new WechatPay2Credentials(mchId,
            new PrivateKeySigner(mchSerialNo, merchantPrivateKey)), apiV3Key.getBytes(StandardCharsets.UTF_8));
// ... 若有多个商户号，可继续调用putMerchant添加商户信息

// 从证书管理器中获取verifier
verifier = certificatesManager.getVerifier(mchId);
WechatPayHttpClientBuilder builder = WechatPayHttpClientBuilder.create()
        .withMerchant(merchantId, merchantSerialNumber, merchantPrivateKey)
        .withValidator(new WechatPay2Validator(verifier))
// ... 接下来，你仍然可以通过builder设置各种参数，来配置你的HttpClient

// 通过WechatPayHttpClientBuilder构造的HttpClient，会自动的处理签名和验签，并进行证书自动更新
CloseableHttpClient httpClient = builder.build();

// 后面跟使用Apache HttpClient一样
CloseableHttpResponse response = httpClient.execute(...);
```

### 风险

因为不需要传入微信支付平台证书，CertificatesManager 在首次更新证书时**不会验签**，也就无法确认应答身份，可能导致下载错误的证书。

但下载时会通过 **HTTPS**、**AES 对称加密**来保证证书安全，所以可以认为，在使用官方 JDK、且 APIv3 密钥不泄露的情况下，CertificatesManager 是**安全**的。

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

1. 使用`WechatPayUploadHttpPost`构造一个上传的`HttpPost`，需设置待上传文件的文件名，SHA256摘要，文件的输入流。在`0.4.1`及以上版本，支持设置媒体文件元信息。
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
// 如需直接设置媒体文件元信息，可使用withFile代替withImage
        .withImage(file.getName(), sha256, ins2)
        .build();
    CloseableHttpResponse response1 = httpClient.execute(request);
  }
}
```

[AutoUpdateVerifierTest.uploadImageTest](/src/test/java/com/wechat/pay/contrib/apache/httpclient/AutoUpdateVerifierTest.java#90)是一个更完整的示例。

## 回调通知的验签与解密
版本>=`0.4.2`可使用 `NotificationHandler.parse(request)` 对回调通知验签和解密：

1. 使用`NotificationRequest`构造一个回调通知请求体，需设置应答平台证书序列号、应答随机串、应答时间戳、应答签名串、应答主体。
2. 使用`NotificationHandler`构造一个回调通知处理器，需设置验证器、apiV3密钥。调用`parse(request)`得到回调通知`notification`。

示例请参考下列代码。
```java
// 构建request，传入必要参数
 NotificationRequest request = new NotificationRequest.Builder().withSerialNumber(wechatPaySerial)
        .withNonce(nonce)
        .withTimestamp(timestamp)
        .withSignature(signature)
        .withBody(body)
        .build();
NotificationHandler handler = new NotificationHandler(verifier, apiV3Key.getBytes(StandardCharsets.UTF_8));
// 验签和解析请求体
Notification notification = handler.parse(request);
// 从notification中获取解密报文
System.out.println(notification.getDecryptData());
```

[NotificationHandlerTest](src/test/java/com/wechat/pay/contrib/apache/httpclient/NotificationHandlerTest.java#105)是一个更完整的示例。

### 异常处理
`parse(request)`可能返回以下异常，推荐对异常打日志或上报监控。
- 抛出`ValidationException`时，请先检查传入参数是否与回调通知参数一致。若一致，说明参数可能被恶意篡改导致验签失败。
- 抛出`ParseException`时，请先检查传入包体是否与回调通知包体一致。若一致，请检查AES密钥是否正确设置。若正确，说明包体可能被恶意篡改导致解析失败。

## 常见问题

### 如何加载商户私钥

商户申请商户API证书时，会生成商户私钥，并保存在本地证书文件夹的文件`apiclient_key.pem`中。商户开发者可以使用方法`PemUtil.loadPrivateKey()`加载证书。

```java
# 示例：私钥存储在文件
PrivateKey merchantPrivateKey = PemUtil.loadPrivateKey(
        new FileInputStream("/path/to/apiclient_key.pem"));

# 示例：私钥为String字符串
PrivateKey merchantPrivateKey = PemUtil.loadPrivateKey(
        new ByteArrayInputStream(privateKey.getBytes("utf-8")));
```

### 如何下载平台证书？

使用`WechatPayHttpClientBuilder`需要调用`withWechatPay`设置[微信支付平台证书](https://wechatpay-api.gitbook.io/wechatpay-api-v3/ren-zheng/zheng-shu#ping-tai-zheng-shu)，而平台证书又只能通过调用[获取平台证书接口](https://wechatpay-api.gitbook.io/wechatpay-api-v3/jie-kou-wen-dang/ping-tai-zheng-shu#huo-qu-ping-tai-zheng-shu-lie-biao)下载。为了解开"死循环"，你可以在第一次下载平台证书时，按照下述方法临时"跳过”应答签名的验证。

```java
CloseableHttpClient httpClient = WechatPayHttpClientBuilder.create()
  .withMerchant(mchId, mchSerialNo, merchantPrivateKey)
  .withValidator(response -> true) // NOTE: 设置一个空的应答签名验证器，**不要**用在业务请求
  .build();
```

**注意**：业务请求请使用标准的初始化流程，务必验证应答签名。

### 如何下载账单

因为下载的账单文件可能会很大，为了平衡系统性能和签名验签的实现成本，[账单下载API](https://pay.weixin.qq.com/wiki/doc/apiv3/wxpay/pay/bill/chapter3_3.shtml)被分成了两个步骤：

1. `/v3/bill/tradebill` 获取账单下载链接和账单摘要
2. `/v3/billdownload/file` 账单文件下载，请求需签名但应答不签名

因为第二步不包含应答签名，我们可以参考上一个问题下载平台证书的方法，使用`withValidator(response -> true)`“跳过”应答的签名校验。

**注意**：开发者在下载文件之后，应使用第一步获取的账单摘要校验文件的完整性。

### 证书和回调解密需要的AesGcm解密在哪里？

请参考[AesUtil.Java](https://github.com/wechatpay-apiv3/wechatpay-apache-httpclient/blob/master/src/main/java/com/wechat/pay/contrib/apache/httpclient/util/AesUtil.java)。

### 我想使用以前的版本，要怎么办

之前的版本可以从 [jitpack](https://jitpack.io/#wechatpay-apiv3/wechatpay-apache-httpclient) 获取。例如希望使用0.1.6版本，gradle中可以使用以下的方式。

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

### 如何解决Jackson NoSuchMethodError报错

在之前的版本中，我们出于安全考虑升级 Jackson 到`2.12`，并使用了`2.11`版本中新增的方法`readValue(String src, Class<T> valueType)`。如果你的项目所依赖的其他组件又依赖了低于`2.11`版本的 Jackson ，可能会出现依赖冲突。

我们建议有能力的开发者，升级冲突组件至较新的兼容版本。例如，issue [#125](https://github.com/wechatpay-apiv3/wechatpay-apache-httpclient/issues/125) 版本 <`2.3.x` 的 SpringBoot 官方已不再维护，继续使用可能会有安全隐患。

如果难以升级，你可以用下面的方式引入 [jackson-bom](https://github.com/FasterXML/jackson-bom) 来升级 Jackson 版本。根据[通用漏洞披露信息](https://cve.mitre.org/)，我们推荐升级到`2.13.2.20220328`版本。

#### Gradle
```groovy
implementation(platform("com.fasterxml.jackson:jackson-bom:2.13.2.20220328"))
```
#### Maven
```xml
<parent>
    <groupId>com.fasterxml.jackson</groupId>
    <artifactId>jackson-bom</artifactId>
    <version>2.13.2.20220328</version>
</parent>
```

如果出现其他组件的 `NoSuchMethodError` 报错，一般是依赖冲突导致。我们可以参考下面的解决思路：
1. 从报错信息中找到出现问题的组件（如上面的 Jackson ）。根据你的项目的构建方式，选择 [Gradle](https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html#sec:listing_dependencies) 或 [Maven](https://maven.apache.org/plugins/maven-dependency-plugin/tree-mojo.html) 工具列出项目的依赖关系树，找到问题组件的所有版本号。
2. 从报错信息中找到正确的组件版本号。一般来说，导致报错的原因是使用的组件版本太低，所以我们可以找组件在依赖关系树中最新的版本号。
3. 指定组件版本。如果组件提供了 bom 依赖，可以使用上述方式引入 bom 依赖来指定版本。否则，根据你的项目的构建方式，选择 [Gradle](https://docs.gradle.org/current/userguide/dependency_constraints.html#sec:adding-constraints-transitive-deps) 或 [Maven](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html) 的方式来指定版本。

### 更多常见问题

请看商户平台的[常见问题](https://pay.weixin.qq.com/wiki/doc/apiv3_partner/wechatpay/wechatpay7_0.shtml)，或者[这里](https://wechatpay-api.gitbook.io/wechatpay-api-v3/chang-jian-wen-ti)。

## 联系我们

如果你发现了**BUG**或者有任何疑问、建议，请通过issue进行反馈。

也欢迎访问我们的[开发者社区](https://developers.weixin.qq.com/community/pay)。
