package com.ruoyi.web.controller.metal;
import java.math.BigDecimal;import java.util.*;import jakarta.validation.Valid;import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;import org.springframework.security.access.prepost.PreAuthorize;
import com.ruoyi.common.annotation.Log;import com.ruoyi.common.enums.BusinessType;import com.ruoyi.common.core.controller.BaseController;import com.ruoyi.common.core.domain.AjaxResult;import com.ruoyi.metal.service.SemiFlowService;
@RestController @RequestMapping("/metal")
public class SemiFlowController extends BaseController {
 private final SemiFlowService service;public SemiFlowController(SemiFlowService service){this.service=service;}
 public record Close(@NotBlank String requestId,@NotNull @Min(0) Integer version,@NotBlank @Size(max=500) String reason,@NotNull Boolean confirmed){}
 public record Release(@NotBlank String requestId,@NotNull @Min(0) Integer version,@Min(1) @Max(1000000) long quantity,@NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=14,fraction=6) BigDecimal netWeight,@NotBlank @Size(max=500) String reason){}
 public record Cancel(@NotBlank String requestId,@NotNull @Min(0) Integer version,@NotBlank @Size(max=500) String reason){}
 @GetMapping("/work-order/{id}/semi-flow") @PreAuthorize("@ss.hasPermi('metal:work-order:query')")
 public AjaxResult overview(@PathVariable Long id){return success(clean(service.overview(id)));}
 @PostMapping("/work-order/{id}/partial-close") @PreAuthorize("@ss.hasPermi('metal:work-order:partial-close')") @Log(title="工单部分完工关闭",businessType=BusinessType.UPDATE)
 public AjaxResult close(@PathVariable Long id,@Valid @RequestBody Close body){service.close(id,body.requestId(),body.version(),body.reason(),body.confirmed(),false);return success();}
 @PostMapping("/work-order/{id}/reopen") @PreAuthorize("@ss.hasPermi('metal:work-order:reopen')") @Log(title="撤销部分关闭",businessType=BusinessType.UPDATE)
 public AjaxResult reopen(@PathVariable Long id,@Valid @RequestBody Close body){service.close(id,body.requestId(),body.version(),body.reason(),body.confirmed(),true);return success();}
 @PostMapping("/warehouse/inventory/{id}/semi-release") @PreAuthorize("@ss.hasPermi('metal:semi-release:add')") @Log(title="剩余半成品释放",businessType=BusinessType.INSERT)
 public AjaxResult release(@PathVariable Long id,@Valid @RequestBody Release body){return success().put("id",String.valueOf(service.release(id,body.requestId(),body.version(),body.quantity(),body.netWeight(),body.reason())));}
 @PostMapping("/warehouse/semi-release/{id}/cancel") @PreAuthorize("@ss.hasPermi('metal:semi-release:cancel')") @Log(title="撤销半成品释放",businessType=BusinessType.UPDATE)
 public AjaxResult cancel(@PathVariable Long id,@Valid @RequestBody Cancel body){service.cancel(id,body.requestId(),body.version(),body.reason());return success();}
 private Object clean(Object value){if(value instanceof Map<?,?> m){var out=new LinkedHashMap<String,Object>();m.forEach((key,v)->{String name=key.toString();StringBuilder n=new StringBuilder();boolean upper=false;for(char c:name.toCharArray()){if(c=='_'){upper=true;continue;}n.append(upper?Character.toUpperCase(c):c);upper=false;}String k=n.toString();out.put(k,v!=null&&(k.endsWith("Id")||k.equals("id"))?v.toString():clean(v));});return out;}if(value instanceof List<?> l)return l.stream().map(this::clean).toList();if(value instanceof BigDecimal d)return d.toPlainString();return value;}
}
