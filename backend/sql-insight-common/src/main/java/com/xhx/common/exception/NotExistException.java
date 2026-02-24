package com.xhx.common.exception;

/**
 * @author master
 */
public class NotExistException extends BaseException{

    public NotExistException(String message) {
        super(500, message);
    }


    public NotExistException(int code, String message) {
        super(code, message);
    }
}
