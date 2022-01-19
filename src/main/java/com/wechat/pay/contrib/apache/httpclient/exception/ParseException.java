package com.wechat.pay.contrib.apache.httpclient.exception;

/**
 * @author lianup
 */
public class ParseException extends WechatPayException {

    private static final long serialVersionUID = 4300538230471368120L;

    public ParseException(String message) {
        super(message);
    }

    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
