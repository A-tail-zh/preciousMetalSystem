package com.ruoyi.metal.catalog;

import java.util.List;
import java.util.function.BiConsumer;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomerTextValidationTest {
    private static final String EDGES = "\t \r\n\u3000\u00A0\u2007\u202F\uFEFF";
    private static final List<BiConsumer<CustomerRecord, String>> REQUIRED = List.of(
            CustomerRecord::setName, CustomerRecord::setCompanyName, CustomerRecord::setAddress,
            CustomerRecord::setContactName, CustomerRecord::setContactPhone);

    private static CustomerRecord validCustomer() {
        var row = new CustomerRecord();
        row.setName("客户");
        row.setCompanyName("客户公司");
        row.setAddress("客户地址");
        row.setContactName("联络人");
        row.setContactPhone("13800138000");
        return row;
    }

    @Test void rejectsWhitespaceOnlyRequiredFields() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            for (String blank : List.of(" ", "\t\r\n", "\u3000", "\u00A0", "\u2007", "\u202F", "\uFEFF", EDGES)) {
                for (var setter : REQUIRED) {
                    var row = validCustomer();
                    setter.accept(row, blank);
                    assertFalse(validator.validate(row).isEmpty(), "Whitespace-only required field must fail");
                }
            }
        }
    }

    @Test void trimsEveryFieldBeforeLengthAndPhoneValidation() {
        var row = validCustomer();
        row.setName(EDGES + "客".repeat(100) + EDGES);
        row.setCompanyName(EDGES + "司".repeat(100) + EDGES);
        row.setAddress(EDGES + "址".repeat(500) + EDGES);
        row.setContactName(EDGES + "人".repeat(100) + EDGES);
        row.setContactPhone(EDGES + "13800138000" + EDGES);
        row.setDepartment(EDGES + "部".repeat(100) + EDGES);
        assertEquals("客".repeat(100), row.getName());
        assertEquals("司".repeat(100), row.getCompanyName());
        assertEquals("址".repeat(500), row.getAddress());
        assertEquals("人".repeat(100), row.getContactName());
        assertEquals("13800138000", row.getContactPhone());
        assertEquals("部".repeat(100), row.getDepartment());
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(row).isEmpty());
        }
    }

    @Test void preservesInternalWhitespaceAndExactIdentityText() {
        for (String text : List.of("客户 甲", "客户\u00A0甲", "客户\u2007甲", "客户\u202F甲", "客户\uFEFF甲", "Acme & Co")) {
            var row = validCustomer();
            row.setName(EDGES + text + EDGES);
            row.setCompanyName(EDGES + text + EDGES);
            row.setAddress(EDGES + text + EDGES);
            row.setContactName(EDGES + text + EDGES);
            row.setDepartment(EDGES + text + EDGES);
            assertEquals(text, row.getName());
            assertEquals(text, row.getCompanyName());
            assertEquals(text, row.getAddress());
            assertEquals(text, row.getContactName());
            assertEquals(text, row.getDepartment());
        }
    }

    @Test void keepsOptionalDepartmentNullForAllBoundaryWhitespace() {
        var row = validCustomer();
        for (String blank : List.of("", " ", "\u00A0", "\u2007", "\u202F", "\uFEFF", EDGES)) {
            row.setDepartment(blank);
            assertNull(row.getDepartment());
        }
        row.setDepartment(null);
        assertNull(row.getDepartment());
        row.setName(null);
        assertNull(row.getName());
    }

    @Test void stillRejectsOverlengthTextAndInvalidPhones() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var row = validCustomer();
            row.setName(EDGES + "客".repeat(101) + EDGES);
            assertTrue(validator.validate(row).stream().anyMatch(v -> v.getPropertyPath().toString().equals("name")));
            row = validCustomer();
            row.setDepartment(EDGES + "部".repeat(101) + EDGES);
            assertTrue(validator.validate(row).stream().anyMatch(v -> v.getPropertyPath().toString().equals("department")));
            for (String phone : List.of("12800138000", "1380013800", "138001380000", "13800\u00A0138000", "+8613800138000")) {
                row = validCustomer();
                row.setContactPhone(EDGES + phone + EDGES);
                assertTrue(validator.validate(row).stream().anyMatch(v -> v.getPropertyPath().toString().equals("contactPhone")));
            }
        }
    }
}
