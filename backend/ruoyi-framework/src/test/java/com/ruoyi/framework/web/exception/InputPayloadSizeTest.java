package com.ruoyi.framework.web.exception;

import java.io.*;
import java.util.Arrays;
import com.ruoyi.common.core.domain.model.LoginBody;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import static org.junit.jupiter.api.Assertions.*;

class InputPayloadSizeTest {
    private byte[] json(int size) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, (byte) ' ');
        bytes[0] = '{'; bytes[1] = '}';
        return bytes;
    }

    private HttpInputMessage message(byte[] bytes) {
        return new HttpInputMessage() {
            public HttpHeaders getHeaders() { return new HttpHeaders(); }
            public InputStream getBody() { return new ByteArrayInputStream(bytes); }
        };
    }

    @Test void oversizedJsonIsRejectedEvenWithoutContentLength() {
        var error = assertThrows(InputPayloadAdvice.InvalidFields.class, () ->
            new InputPayloadAdvice().beforeBodyRead(message(json(10 * 1024 * 1024 + 1)), null, LoginBody.class, null));
        assertEquals("PayloadSize", error.fields.get(0).get("code"));
    }

    @Test void aRequestAtTheLimitReachesTheConverterUnchanged() throws Exception {
        var bytes = json(10 * 1024 * 1024);
        var result = new InputPayloadAdvice().beforeBodyRead(message(bytes), null, LoginBody.class, null);
        assertArrayEquals(bytes, result.getBody().readAllBytes());
    }
}
