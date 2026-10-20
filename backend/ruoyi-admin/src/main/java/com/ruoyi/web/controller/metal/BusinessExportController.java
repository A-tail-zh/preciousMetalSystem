package com.ruoyi.web.controller.metal;
import org.springframework.web.bind.annotation.*;import org.springframework.security.access.prepost.PreAuthorize;import jakarta.servlet.http.HttpServletResponse;import com.ruoyi.metal.service.BusinessExportService;import com.ruoyi.metal.catalog.*;import com.ruoyi.common.annotation.Log;import com.ruoyi.common.enums.BusinessType;
@RestController public class BusinessExportController {
 private final BusinessExportService service;public BusinessExportController(BusinessExportService s){service=s;}
 private interface Export {long write(java.io.OutputStream output)throws Exception;}
 private void send(HttpServletResponse response,String name,Export exporter)throws Exception{var file=java.nio.file.Files.createTempFile("business-export-",".xlsx");try{long count;try(var output=java.nio.file.Files.newOutputStream(file)){count=exporter.write(output);}headers(response,name);response.setHeader("X-Export-Count",Long.toString(count));java.nio.file.Files.copy(file,response.getOutputStream());}finally{java.nio.file.Files.deleteIfExists(file);}}
 private void headers(HttpServletResponse r,String name){r.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");r.setHeader("Content-Disposition","attachment; filename="+name+".xlsx");}
 @PostMapping("/system/user/export") @PreAuthorize("@ss.hasPermi('system:user:export')") @Log(title="人员条件导出",businessType=BusinessType.EXPORT,isSaveResponseData=false)
 public void staff(@ModelAttribute ListQuery q,HttpServletResponse r)throws Exception{q.validate();send(r,"staff",out->service.staff(q,out));}
 @PostMapping("/metal/warehouse-order/export") @PreAuthorize("@ss.hasPermi('metal:warehouse-order:export')") @Log(title="仓储工单条件导出",businessType=BusinessType.EXPORT,isSaveResponseData=false)
 public void orders(@ModelAttribute OrderListQuery q,HttpServletResponse r)throws Exception{q.validate();send(r,"warehouse-orders",out->service.orders(q,out));}
}
