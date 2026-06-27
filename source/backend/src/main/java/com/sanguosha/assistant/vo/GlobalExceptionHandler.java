package com.sanguosha.assistant.vo;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(AppException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleAppException(AppException ex) {
        if (ex.getResultCode() == ResultCode.FAILED) {
            return Result.failed(ex.getMessage());
        }
        return Result.failed(ex.getResultCode());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleValidation(Exception ex) {
        return Result.failed(ResultCode.VALIDATE_FAILED);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResource(NoResourceFoundException ex) {
        return Result.failed("资源不存在");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleException(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = "服务器内部错误";
        }
        return Result.failed(message);
    }
}
