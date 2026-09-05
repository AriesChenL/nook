package com.lynn.nook.pay.controller;

import com.lynn.nook.common.exception.BusinessException;
import com.lynn.nook.common.result.ResultCode;
import com.lynn.nook.pay.service.StripeWebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class StripeWebhookControllerTest {

    private StripeWebhookService service;
    private StripeWebhookController controller;

    @BeforeEach
    void setUp() {
        service = mock(StripeWebhookService.class);
        controller = new StripeWebhookController(service);
    }

    @Test
    void successReturnsTwoHundred() {
        assertThat(controller.receive("{}", "sig").getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void signatureFailureReturnsBadRequestWithoutRetry() {
        doThrow(new BusinessException(ResultCode.PAY_WEBHOOK_SIGNATURE_INVALID))
                .when(service).handle("{}", "bad");
        assertThat(controller.receive("{}", "bad").getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void processingFailureReturnsServerErrorForStripeRetry() {
        doThrow(new IllegalStateException("database unavailable"))
                .when(service).handle("{}", "sig");
        assertThat(controller.receive("{}", "sig").getStatusCode().value()).isEqualTo(500);
    }
}
