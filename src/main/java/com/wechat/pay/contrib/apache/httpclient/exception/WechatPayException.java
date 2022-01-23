package com.wechat.pay.contrib.apache.httpclient.exception;

/**
 * @author lianup
 */
public abstract class WechatPayException extends Exception {

    private static final long serialVersionUID = -5059029681600588999L;

    public WechatPayException(String message) {
        super(message);
    }

    public WechatPayException(String message, Throwable cause) {
        super(message, cause);
    }

}
