package com.lynn.nook.common.exception;

import com.lynn.nook.common.result.Result;
import com.lynn.nook.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException ex) {
        log.warn("BusinessException: code={}, msg={}", ex.getCode(), ex.getMessage());
        return ResponseEntity.ok(Result.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<Result<Void>> handleValidation(Exception ex) {
        String msg = ResultCode.BAD_REQUEST.getMessage();
        if (ex instanceof MethodArgumentNotValidException manve) {
            FieldError fe = manve.getBindingResult().getFieldError();
            if (fe != null) msg = fe.getField() + ": " + fe.getDefaultMessage();
        } else if (ex instanceof BindException be) {
            FieldError fe = be.getBindingResult().getFieldError();
            if (fe != null) msg = fe.getField() + ": " + fe.getDefaultMessage();
        }
        return ResponseEntity.badRequest()
                .body(Result.fail(ResultCode.BAD_REQUEST.getCode(), msg));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleAny(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(ResultCode.SERVER_ERROR));
    }
}
