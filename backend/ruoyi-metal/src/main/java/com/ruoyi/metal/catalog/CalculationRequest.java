package com.ruoyi.metal.catalog;
import java.util.Map;
import jakarta.validation.constraints.*;
public record CalculationRequest(String category, @NotNull Map<String,String> dimensions,
 @NotNull @Min(1) @Max(1000000) Integer quantity, @Positive Long metalTypeId, String productType,
 @Pattern(regexp="MATERIAL_CUSTOM|DIRECT_CUSTOM|SHORTAGE_CUSTOM",message="请选择有效加工类型") String processingType,
 @Pattern(regexp="[0-9]{1,14}(\\.[0-9]{1,6})?",message="来料粉料重量须为非负十进制数字，最多14位整数、6位小数") String powderWeight,
 @Pattern(regexp="[0-9]{1,14}(\\.[0-9]{1,6})?",message="来料非粉料重量须为非负十进制数字，最多14位整数、6位小数") String nonPowderWeight,
 @Pattern(regexp="[0-9]{1,14}(\\.[0-9]{1,6})?",message="理论损失重量须为非负十进制数字，最多14位整数、6位小数") String lossWeight) {
 public CalculationRequest(String category,Map<String,String> dimensions,Integer quantity,Long metalTypeId,String productType){this(category,dimensions,quantity,metalTypeId,productType,null,null,null,null);}
 public CalculationRequest(String category,Map<String,String> dimensions,Integer quantity){this(category,dimensions,quantity,null,null);}
}
