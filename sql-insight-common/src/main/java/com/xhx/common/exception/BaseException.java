package com.xhx.common.exception;

import lombok.Getter;

/**
 * @author master
 */
@Getter
public abstract class BaseException extends RuntimeException {
    private final int code;

    public BaseException(int code, String message) {
        super(message);
        this.code = code;
    }
}