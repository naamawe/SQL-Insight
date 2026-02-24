package com.xhx.common.exception;

/**
 * @author master
 */
public class ConnectionException extends BaseException {

    public ConnectionException(String message) {
        super(500, message);
    }

    public ConnectionException(int code, String message) {
        super(code, message);
    }
}