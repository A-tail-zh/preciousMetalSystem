package com.ruoyi.metal.catalog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.TreeMap;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.metal.catalog.WarehouseRequests.Order;
import com.ruoyi.metal.catalog.WarehouseRequests.Line;

public final class WarehouseFlowRequests {
    private WarehouseFlowRequests() {}
    public record Create(@NotBlank String requestId,@NotNull @Positive Long factoryId,
                         @NotEmpty @Size(max=1000) List<@NotNull @Valid Order> documents) {}
    public record Measured(@NotNull @Positive Long lineId,
                           @NotNull @DecimalMin("0") @Digits(integer=14,fraction=6)
                           @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal netWeight,
                           @Min(0) Long quantity,@Size(max=500) String differenceReason) {}
    public record Confirm(@NotBlank String requestId,@NotNull @Min(0) Integer version,
                          @NotEmpty @Size(max=1000) List<@NotNull @Valid Measured> lines) {}
    public record Change(@NotBlank String requestId,@NotNull @Min(0) Integer version,
                         @NotBlank @Size(max=500) String reason,
                         @Size(max=1000) List<@NotNull @Valid Order> documents) {}
    public record HandoverDocument(@NotBlank String type,@NotNull @Positive Long warehouseId,
                                  @NotNull @Positive Long keeperId,@Positive Long reviewerId,
                                  @NotEmpty @Size(max=1000) List<@NotNull @Valid Line> lines) {}
    public record Wip(@NotBlank @Size(max=160) String groupKey,
                      @NotNull @DecimalMin("0") @Digits(integer=14,fraction=2) BigDecimal remainingWeight) {}
    public record Handover(@NotBlank String requestId,@NotNull @Min(0) Integer version,
                           @NotNull LocalDate orderDate,@NotNull Boolean physicalChecked,
                           @NotBlank @Size(max=16) String residualMode,@Size(max=500) String reason,
                           @NotEmpty @Size(max=1000) List<@NotNull @Valid HandoverDocument> documents,
                           @Size(max=1000) List<@NotNull @Valid Wip> wipBalances,
                           @Positive Long flowId) {
        public Handover(String requestId,Integer version,LocalDate orderDate,Boolean physicalChecked,
                        String residualMode,String reason,List<HandoverDocument> documents) {
            this(requestId,version,orderDate,physicalChecked,residualMode,reason,documents,null,null);
        }
    }
    public record Closeout(@NotBlank String requestId,@NotNull @Min(0) Integer version,
                           @NotBlank String mode,@NotNull @Positive Long keeperId,
                           @Positive Long offsetWorkOrderId,
                           @NotNull @DecimalMin("0") @Digits(integer=14,fraction=6) BigDecimal weight,
                           @NotBlank @Size(max=500) String reason,
                           @Size(max=1000) Map<@NotNull @Positive Long,@NotNull @Positive @Max(1000000) Long> pieceQuantities) {
        public Closeout { if(pieceQuantities!=null)pieceQuantities=Collections.unmodifiableMap(new TreeMap<>(pieceQuantities)); }
        public Closeout(String requestId,Integer version,String mode,Long keeperId,Long offsetWorkOrderId,
                        BigDecimal weight,String reason) {
            this(requestId,version,mode,keeperId,offsetWorkOrderId,weight,reason,null);
        }
    }
}
