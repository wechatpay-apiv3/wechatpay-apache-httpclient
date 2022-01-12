package com.wechat.pay.contrib.apache.httpclient;

import static org.apache.http.HttpHeaders.ACCEPT;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;

import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;

/**
 * @author xy-peng
 */
public class WechatPayUploadHttpPost extends HttpPost {

    private final String meta;

    private WechatPayUploadHttpPost(URI uri, String meta) {
        super(uri);
        this.meta = meta;
    }

    public String getMeta() {
        return meta;
    }

    public static class Builder {

        private final URI uri;
        private String fileName;
        private String fileSha256;
        private InputStream fileInputStream;
        private ContentType fileContentType;
        private String meta;

        public Builder(URI uri) {
            this.uri = uri;
        }

        public Builder withImage(String fileName, String fileSha256, InputStream inputStream) {
            if (fileName == null || fileName.isEmpty()) {
                throw new IllegalArgumentException("文件名称为空");
            }
            if (fileSha256 == null || fileSha256.isEmpty()) {
                throw new IllegalArgumentException("文件摘要为空");
            }
            if (inputStream == null) {
                throw new IllegalArgumentException("图片文件为空");
            }

            this.fileName = fileName;
            this.fileSha256 = fileSha256;
            this.fileInputStream = inputStream;
            setFileContentType(fileName);
            this.meta = String.format("{\"filename\":\"%s\",\"sha256\":\"%s\"}", fileName, fileSha256);
            return this;
        }

        public Builder withFile(String fileName, String meta, InputStream inputStream) {
            if (fileName == null || fileName.isEmpty()) {
                throw new IllegalArgumentException("文件名称为空");
            }
            if (meta == null) {
                throw new IllegalArgumentException("媒体文件元信息为空");
            }
            if (inputStream == null) {
                throw new IllegalArgumentException("文件为空");
            }
            this.fileName = fileName;
            this.fileInputStream = inputStream;
            setFileContentType(fileName);
            this.meta = meta;
            return this;
        }

        private void setFileContentType(String fileName) {
            String mimeType = URLConnection.guessContentTypeFromName(fileName);
            if (mimeType == null) {
                // guess this is a video uploading
                this.fileContentType = ContentType.APPLICATION_OCTET_STREAM;
            } else {
                this.fileContentType = ContentType.create(mimeType);
            }
        }

        public WechatPayUploadHttpPost build() {
            if (uri == null) {
                throw new IllegalArgumentException("缺少上传图片接口URL");
            }

            WechatPayUploadHttpPost request = new WechatPayUploadHttpPost(uri, meta);

            MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
            entityBuilder.setMode(HttpMultipartMode.RFC6532)
                    .addBinaryBody("file", fileInputStream, fileContentType, fileName)
                    .addTextBody("meta", meta, APPLICATION_JSON);
            request.setEntity(entityBuilder.build());
            request.addHeader(ACCEPT, APPLICATION_JSON.toString());
            return request;
        }
    }
}
