package com.ruoyi.metal.service;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.metal.catalog.*;
import com.ruoyi.metal.mapper.ProcessMapper;
import com.ruoyi.metal.security.FactoryScope;
import com.ruoyi.common.exception.ServiceException;
/**
 * 工序业务服务，负责业务校验、数据处理和相关持久化操作。
 */
@Service public class ProcessService extends MasterService<ProcessRecord> {
 private final JdbcTemplate jdbc;
 public ProcessService(ProcessMapper mapper,FactoryScope scope,JdbcTemplate jdbc){super(mapper,scope);this.jdbc=jdbc;}
 /**
  * 检查业务引用和保护规则，不满足删除条件时抛出异常。
  */
 @Override protected void deletable(ProcessRecord row){WorkOrderReferences.process(jdbc,row.getId());WarehouseReferences.process(jdbc,row.getId());if(jdbc.queryForObject("SELECT COUNT(*) FROM metal_product_process pp JOIN metal_product p ON p.product_id=pp.product_id AND p.del_flag='0' WHERE pp.process_id=?",Integer.class,row.getId())>0)throw new ServiceException("工序被产品引用，不能删除："+row.getName());}
}
