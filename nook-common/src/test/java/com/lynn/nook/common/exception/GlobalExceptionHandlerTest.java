package com.lynn.nook.common.exception;

import com.lynn.nook.common.result.Result;
import com.lynn.nook.common.result.ResultCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpMethod;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void business_returns200WithBusinessCode() {
        ResponseEntity<Result<Void>> resp = handler.handleBusiness(
                new BusinessException(ResultCode.USER_NOT_FOUND));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getCode()).isEqualTo(ResultCode.USER_NOT_FOUND.getCode());
    }

    @Test
    void notReadable_returns400WithFriendlyMessage() {
        ResponseEntity<Result<Void>> resp = handler.handleNotReadable(
                new HttpMessageNotReadableException("bad", (org.springframework.http.HttpInputMessage) null));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getCode()).isEqualTo(ResultCode.BAD_REQUEST.getCode());
        assertThat(resp.getBody().getMessage()).contains("请求体");
    }

    @Test
    void missingParam_returns400WithParamName() {
        ResponseEntity<Result<Void>> resp = handler.handleMissingParam(
                new MissingServletRequestParameterException("q", "String"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getMessage()).contains("q");
    }

    @Test
    void methodNotSupported_returns405() {
        ResponseEntity<Result<Void>> resp = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("DELETE"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(resp.getBody().getCode()).isEqualTo(ResultCode.METHOD_NOT_ALLOWED.getCode());
    }

    @Test
    void noResourceFound_returns404NotFiveHundred() {
        // Spring Boot 3.2+ 对未知路径抛 NoResourceFoundException，必须当 404 而非落到 500 兜底
        ResponseEntity<Result<Void>> resp = handler.handleNotFound(
                new NoResourceFoundException(HttpMethod.GET, "/actuator/health"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
    }

    @Test
    void any_returns500() {
        ResponseEntity<Result<Void>> resp = handler.handleAny(new RuntimeException("boom"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().getCode()).isEqualTo(ResultCode.SERVER_ERROR.getCode());
    }
}
