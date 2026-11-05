package com.ruoyi.web.controller.common;

import com.ruoyi.framework.web.exception.GlobalExceptionHandler;
import com.ruoyi.common.exception.user.*;
import com.ruoyi.common.utils.MessageUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.bind.annotation.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LoginFailureMessageTest {
    @RestController
    static class LoginFailures {
        @PostMapping("/login-failure/{kind}")
        void login(@PathVariable String kind) {
            throw switch (kind) {
                case "password" -> new UserPasswordNotMatchException();
                case "unknown-user" -> new UserNotExistsException();
                case "captcha" -> new CaptchaException();
                case "expired" -> new CaptchaExpireException();
                default -> new IllegalStateException("internal database credentials must not appear");
            };
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"password", "unknown-user", "captcha", "expired"})
    void expectedLoginFailuresKeepTheirSpecificMessage(String kind) throws Exception {
        String expected = switch (kind) {
            case "captcha" -> "验证码错误";
            case "expired" -> "验证码已失效";
            default -> "账号或密码错误";
        };
        try (var messages=mockStatic(MessageUtils.class)) {
            messages.when(()->MessageUtils.message(anyString(),nullable(Object[].class))).thenReturn(expected);
            var mvc=MockMvcBuilders.standaloneSetup(new LoginFailures()).setControllerAdvice(new GlobalExceptionHandler()).build();
            mvc.perform(post("/login-failure/"+kind)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500)).andExpect(jsonPath("$.msg").value(expected));
        }
    }

    @Test void unexpectedTechnicalFailureStaysGeneric() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new LoginFailures()).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/login-failure/system")).andExpect(status().isOk())
            .andExpect(jsonPath("$.msg").value("系统处理失败，请稍后重试或联系管理员"));
    }
}
