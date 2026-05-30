package com.lynn.nook.common.exception;

import com.lynn.nook.common.result.Result;
import com.lynn.nook.common.result.ResultCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException ex) {
        log.warn("BusinessException: code={}, msg={}", ex.getCode(), ex.getMessage());
        return ResponseEntity.ok(Result.fail(ex.getCode(), ex.getMessage()));
    }

    // -------- 400 类：参数校验 / 反序列化 / 类型不匹配 --------

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
        return badRequest(msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String msg = ResultCode.BAD_REQUEST.getMessage();
        ConstraintViolation<?> first = ex.getConstraintViolations().stream().findFirst().orElse(null);
        if (first != null) msg = first.getPropertyPath() + ": " + first.getMessage();
        return badRequest(msg);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Body not readable: {}", ex.getMostSpecificCause().getMessage());
        return badRequest("请求体格式错误");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        return badRequest("缺少必填参数: " + ex.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return badRequest("参数类型错误: " + ex.getName());
    }

    /**
     * 网关已透传 X-User-Id 给下游；如果调用方绕过网关直连后端，会触发此异常。
     * 当作未授权处理而非 400，避免泄露内部头名。
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Result<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        log.warn("Missing request header: {}", ex.getHeaderName());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.fail(ResultCode.UNAUTHORIZED));
    }

    // -------- 405 / 404 --------

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Result.fail(ResultCode.METHOD_NOT_ALLOWED));
    }

    /**
     * 未匹配任何 handler / 静态资源的路径。
     * Spring Boot 3.2+ 抛 {@link NoResourceFoundException}（旧版是 {@link NoHandlerFoundException}），
     * 两者都要显式处理，否则会落到下方兜底处理器被当成 500（未知路径本应 404）。
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<Result<Void>> handleNotFound(Exception ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.fail(ResultCode.NOT_FOUND));
    }

    // -------- 500 兜底 --------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleAny(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(ResultCode.SERVER_ERROR));
    }

    private ResponseEntity<Result<Void>> badRequest(String msg) {
        return ResponseEntity.badRequest()
                .body(Result.fail(ResultCode.BAD_REQUEST.getCode(), msg));
    }
}
