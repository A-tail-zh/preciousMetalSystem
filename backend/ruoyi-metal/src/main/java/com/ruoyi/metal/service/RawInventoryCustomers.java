package com.ruoyi.metal.service;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.catalog.WarehouseRequests.Line;

/** Customer IDs identify raw stock; organization remains the historical name. */
final class RawInventoryCustomers {
    private final JdbcTemplate jdbc;
    RawInventoryCustomers(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    Map<String,Object> selected(Long factory, Line line, boolean lock) {
        String owner=WarehouseFlowRules.newOwner(line.materialOwnership(),line.customerId());
        if("FACTORY".equals(owner))return Map.of("company_name","工厂自有","material_ownership","FACTORY","factory_id",factory);
        if (line.customerId()==null || line.customerVersion()==null)
            throw new ServiceException("请选择当前工厂的客户；旧原料模板请重新下载");
        var rows=jdbc.queryForList("SELECT customer_id,factory_id,customer_name,company_name,address,contact_name,contact_phone,department,version FROM metal_customer WHERE customer_id=? AND del_flag='0'"+(lock?" FOR UPDATE":""),line.customerId());
        if(rows.isEmpty())throw new ServiceException("客户不存在或已删除，请重新选择");
        var customer=new LinkedHashMap<>(rows.get(0));
        if(!factory.equals(((Number)customer.get("factory_id")).longValue()))throw new ServiceException("客户不属于当前工厂",403);
        if(line.customerVersion().intValue()!=((Number)customer.get("version")).intValue())throw new ServiceException("客户资料已变化，请刷新客户选项后重新选择",409);
        customer.put("material_ownership","CUSTOMER");
        return customer;
    }

    List<Map<String,Object>> options(Long factory) {
        return jdbc.queryForList("SELECT CAST(customer_id AS CHAR) id,customer_name name,company_name companyName,address,contact_name contactName,contact_phone contactPhone,department,version FROM metal_customer WHERE factory_id=? AND del_flag='0' ORDER BY company_name,customer_name,customer_id",factory);
    }

    static Map<String,Object> snapshot(Map<String,Object> customer) {
        var result=new LinkedHashMap<String,Object>();
        String[] keys={"name","companyName","address","contactName","contactPhone","department"};
        String[] columns={"customer_name","company_name","address","contact_name","contact_phone","department"};
        for(int i=0;i<keys.length;i++)result.put(keys[i],customer.get(columns[i]));
        return result;
    }

    static void inherited(Line line, Long source) {
        if(line.customerId()!=null&&!Objects.equals(line.customerId(),source))throw new ServiceException("客户必须继承原库存或原出库明细，不能更改",403);
    }
}
