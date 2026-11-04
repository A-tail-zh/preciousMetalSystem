package com.ruoyi.metal.catalog;

import jakarta.validation.constraints.*;

public final class RawOriginRequests {
    private RawOriginRequests() {}

    public record Verification(@NotBlank String requestId, @NotNull @Min(0) Integer version,
            @NotBlank String powderOrigin, @NotBlank String washingStatus,
            @NotBlank @Size(max=500) String evidence, String smeltingStatus) {
        public Verification(String requestId, Integer version, String powderOrigin, String washingStatus, String evidence) {
            this(requestId, version, powderOrigin, washingStatus, evidence, null);
        }
    }
}
