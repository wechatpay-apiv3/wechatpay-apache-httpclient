package com.wechat.pay.contrib.apache.httpclient.notification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 请求体解析结果
 *
 * @author lianup
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Notification {

    @JsonProperty("id")
    private String id;
    @JsonProperty("create_time")
    private String createTime;
    @JsonProperty("event_type")
    private String eventType;
    @JsonProperty("resource_type")
    private String resourceType;
    @JsonProperty("summary")
    private String summary;
    @JsonProperty("resource")
    private Resource resource;
    private String decryptData;

    @Override
    public String toString() {
        return "Notification{" +
                "id='" + id + '\'' +
                ", createTime='" + createTime + '\'' +
                ", eventType='" + eventType + '\'' +
                ", resourceType='" + resourceType + '\'' +
                ", decryptData='" + decryptData + '\'' +
                ", summary='" + summary + '\'' +
                ", resource=" + resource +
                '}';
    }

    public String getId() {
        return id;
    }

    public String getCreateTime() {
        return createTime;
    }

    public String getEventType() {
        return eventType;
    }

    public String getDecryptData() {
        return decryptData;
    }

    public String getSummary() {
        return summary;
    }

    public String getResourceType() {
        return resourceType;
    }

    public Resource getResource() {
        return resource;
    }

    public void setDecryptData(String decryptData) {
        this.decryptData = decryptData;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public class Resource {

        @JsonProperty("algorithm")
        private String algorithm;
        @JsonProperty("ciphertext")
        private String ciphertext;
        @JsonProperty("associated_data")
        private String associatedData;
        @JsonProperty("nonce")
        private String nonce;
        @JsonProperty("original_type")
        private String originalType;

        public String getAlgorithm() {
            return algorithm;
        }

        public String getCiphertext() {
            return ciphertext;
        }

        public String getAssociatedData() {
            return associatedData;
        }

        public String getNonce() {
            return nonce;
        }

        public String getOriginalType() {
            return originalType;
        }

        @Override
        public String toString() {
            return "Resource{" +
                    "algorithm='" + algorithm + '\'' +
                    ", ciphertext='" + ciphertext + '\'' +
                    ", associatedData='" + associatedData + '\'' +
                    ", nonce='" + nonce + '\'' +
                    ", originalType='" + originalType + '\'' +
                    '}';
        }
    }

}
