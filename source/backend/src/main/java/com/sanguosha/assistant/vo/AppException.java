package com.sanguosha.assistant.vo;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {
    private final ResultCode resultCode;

    public AppException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    public AppException(String message) {
        super(message);
        this.resultCode = ResultCode.FAILED;
    }
}
