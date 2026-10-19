package com.ruoyi.web.controller.monitor;
import java.util.*;import org.springframework.web.bind.annotation.*;import org.springframework.security.access.prepost.PreAuthorize;import jakarta.servlet.http.HttpServletResponse;import com.ruoyi.framework.audit.LogQueryService;import com.ruoyi.common.core.domain.AjaxResult;import com.ruoyi.common.annotation.Log;import com.ruoyi.common.enums.BusinessType;
@RestController @RequestMapping("/monitor") public class AuditManagementController{
 private final LogQueryService s;private final AuditExport exporter;public AuditManagementController(LogQueryService s,AuditExport e){this.s=s;exporter=e;}
 @GetMapping("/operlog/actors") @PreAuthorize("@ss.hasPermi('monitor:operlog:list')") public Map<String,Object> actors(@RequestParam(defaultValue="") String keyword,@RequestParam(required=false) Long factoryId,@RequestParam(defaultValue="1") int pageNum){return s.actorOptions(keyword,factoryId,pageNum);}
 @GetMapping("/runtime-log/list") @PreAuthorize("@ss.hasPermi('monitor:runtime-log:list')") public Map<String,Object> list(@RequestParam Map<String,String> p){return s.list("runtime",p,false);}
 @GetMapping("/runtime-log/{id}") @PreAuthorize("@ss.hasPermi('monitor:runtime-log:query')") @Log(title="运行日志详情",businessType=BusinessType.OTHER) public AjaxResult detail(@PathVariable Long id){return AjaxResult.success(s.detail("runtime",id));}
 @GetMapping("/runtime-log/trace/{trace}") @PreAuthorize("@ss.hasPermi('monitor:runtime-log:query')") public AjaxResult trace(@PathVariable String trace){return AjaxResult.success(s.trace(trace));}
 @PostMapping("/runtime-log/export") @PreAuthorize("@ss.hasPermi('monitor:runtime-log:export')") @Log(title="运行日志导出",businessType=BusinessType.EXPORT,isSaveResponseData=false) public void export(@RequestParam Map<String,String> p,HttpServletResponse r)throws Exception{exporter.write("runtime",p,r);}
 @GetMapping("/log-config") @PreAuthorize("@ss.hasPermi('monitor:log-config:query')") public AjaxResult config(){return AjaxResult.success(s.config());}
 @PutMapping("/log-config") @PreAuthorize("@ss.hasPermi('monitor:log-config:edit')") @Log(title="日志配置修改",businessType=BusinessType.UPDATE) public AjaxResult save(@RequestBody Map<String,Object> p){s.config(p);return AjaxResult.success();}
 @GetMapping("/log-config/cleanup-history") @PreAuthorize("@ss.hasPermi('monitor:log-config:query')") public AjaxResult history(){return AjaxResult.success(s.cleanupHistory());}
}
