package com.ruoyi.web.controller.monitor;
import java.util.*;import org.springframework.web.bind.annotation.*;import org.springframework.security.access.prepost.PreAuthorize;import jakarta.servlet.http.HttpServletResponse;import com.ruoyi.common.core.domain.AjaxResult;import com.ruoyi.framework.audit.LogQueryService;import com.ruoyi.common.annotation.Log;import com.ruoyi.common.enums.BusinessType;
@RestController @RequestMapping("/monitor/logininfor") public class SysLogininforController {
 private final LogQueryService service;private final AuditExport exporter;public SysLogininforController(LogQueryService s,AuditExport e){service=s;exporter=e;}
 @Log(title="日志列表访问",businessType=BusinessType.OTHER) @GetMapping("/list") @PreAuthorize("@ss.hasPermi('monitor:logininfor:list')") public Map<String,Object> list(@RequestParam Map<String,String> p){return service.list("login",p,false);}
 @PostMapping("/export") @PreAuthorize("@ss.hasPermi('monitor:logininfor:export')") @Log(title="登录日志导出",businessType=BusinessType.EXPORT,isSaveResponseData=false) public void export(@RequestParam Map<String,String> p,HttpServletResponse r)throws Exception{service.admin();exporter.write("login",p,r);}
 @DeleteMapping({"/clean","/{ids}"}) public AjaxResult disabled(){return AjaxResult.error(403,"审计日志不允许手动删除或清空");}
 @GetMapping("/unlock/{name}") public AjaxResult unlock(){return AjaxResult.error(403,"请通过人员管理处理账号");}
}
