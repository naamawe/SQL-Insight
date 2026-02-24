package com.xhx.common.exception;

/**
 * @author master
 */
public class ServiceException extends BaseException{

    public ServiceException(String message) {
        super(500, message);
    }


    public ServiceException(int code, String message) {
        super(code, message);
    }
}
