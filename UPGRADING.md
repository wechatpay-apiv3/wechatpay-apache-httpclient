# 升级指南
## 从 0.3.0 升级至 0.4.0
版本`0.4.0`提供了支持多商户号的[定时更新平台证书功能](README.md#定时更新平台证书功能)，不兼容版本`0.3.0`。推荐升级方式如下：
- 若你使用了`ScheduledUpdateCertificatesVerifier`，请使用`CertificatesManager`替换：
```diff
-verifier = new ScheduledUpdateCertificatesVerifier(
-                new WechatPay2Credentials(merchantId, new PrivateKeySigner(merchantSerialNumber, merchantPrivateKey)),
-                apiV3Key.getBytes(StandardCharsets.UTF_8));
+// 获取证书管理器实例
+certificatesManager = CertificatesManager.getInstance();
+// 向证书管理器增加需要自动更新平台证书的商户信息
+certificatesManager.putMerchant(mchId, new WechatPay2Credentials(mchId,
+                new PrivateKeySigner(mchSerialNo, merchantPrivateKey)), apiV3Key.getBytes(StandardCharsets.UTF_8));
+// 从证书管理器中获取verifier
+verifier = certificatesManager.getVerifier(mchId);
```
- 若你使用了`getLatestCertificate`方法，请使用`getValidCertificate`方法替换。
