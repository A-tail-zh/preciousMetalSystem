package com.ruoyi.metal.catalog;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
/**
 * 报价单保存数据模型，承载业务字段及适用的参数校验规则。
 */
public record QuotationSaveRequest(@NotBlank @Pattern(regexp="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestId,@NotNull @Positive Long factoryId,@NotNull @Size(min=1,max=100) List<@NotNull @Valid CalculationRequest> items){}
