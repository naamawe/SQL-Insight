package com.xhx.common.exception;

import lombok.Getter;

/**
 * @author master
 */
@Getter
public class LoadingException extends RuntimeException{
    private final Exception exception;

    public LoadingException(String message, Exception exception) {
        super(message);
        this.exception = exception;
    }
}
