package com.ruoyi.web.controller.monitor;
import java.util.*;import org.springframework.web.bind.annotation.*;import org.springframework.security.access.prepost.PreAuthorize;import jakarta.servlet.http.HttpServletResponse;import com.ruoyi.common.core.domain.AjaxResult;import com.ruoyi.framework.audit.LogQueryService;import com.ruoyi.common.annotation.Log;import com.ruoyi.common.enums.BusinessType;
@RestController @RequestMapping("/monitor/operlog") public class SysOperlogController {
 private final LogQueryService service;private final AuditExport exporter;public SysOperlogController(LogQueryService s,AuditExport e){service=s;exporter=e;}
 @Log(title="日志列表访问",businessType=BusinessType.OTHER) @GetMapping("/list") @PreAuthorize("@ss.hasPermi('monitor:operlog:list')") public Map<String,Object> list(@RequestParam Map<String,String> p){return service.list("operation",p,false);}
 @GetMapping("/{id}") @PreAuthorize("@ss.hasPermi('monitor:operlog:query')") @Log(title="操作日志详情",businessType=BusinessType.OTHER) public AjaxResult detail(@PathVariable Long id){return AjaxResult.success(service.detail("operation",id));}
 @PostMapping("/export") @PreAuthorize("@ss.hasPermi('monitor:operlog:export')") @Log(title="操作日志导出",businessType=BusinessType.EXPORT,isSaveResponseData=false) public void export(@RequestParam Map<String,String> p,HttpServletResponse r)throws Exception{exporter.write("operation",p,r);}
 @DeleteMapping({"/clean","/{ids}"}) public AjaxResult disabled(){return AjaxResult.error(403,"审计日志不允许手动删除或清空");}
}
