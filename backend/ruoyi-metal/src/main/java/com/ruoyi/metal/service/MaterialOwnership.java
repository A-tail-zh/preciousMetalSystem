package com.ruoyi.metal.service;

import java.util.Map;
import java.util.Objects;
import com.ruoyi.common.exception.ServiceException;

final class MaterialOwnership {
    private MaterialOwnership() {}
    static String of(Map<String,Object> source) {
        Object value=source.get("material_ownership");
        return value==null?(source.get("customer_id")==null?"UNVERIFIED":"CUSTOMER"):value.toString();
    }
    static void inherit(String requested,Map<String,Object> source) {
        if(requested!=null&&!requested.equals(of(source)))throw new ServiceException("材料归属必须继承来源，不能通过出入库或调整改成另一种归属",403);
    }
    static String rawKey(String owner,Long factory,Long customer,String legacy) {
        return "FACTORY".equals(owner)?"F:"+factory:customer==null?"L:"+legacy:"C:"+customer;
    }
    static boolean sameCustomer(Map<String,Object> stock,Map<String,Object> source,Map<String,Object> target) {
        Object a=source.get("customer_id"),b=target.get("customer_id");
        if(a!=null&&b!=null)return a.toString().equals(b.toString())&&(stock.get("customer_id")==null||b.toString().equals(stock.get("customer_id").toString()));
        return SemiFlowRules.sameCustomer(Objects.toString(source.get("customer"),""),Objects.toString(target.get("customer"),""))
                &&SemiFlowRules.sameCustomer(Objects.toString(stock.get("organization"),""),Objects.toString(target.get("customer"),""));
    }
    static String production(Map<String,Object> stock,Map<String,Object> work) {
        String owner=of(stock),mode=Objects.toString(work.get("processing_type"),"MATERIAL");
        if("UNVERIFIED".equals(owner))throw new ServiceException("历史原料归属未核对，不能作为客户料或厂料领用");
        if("CUSTOMER".equals(owner)) {
            if(!"MATERIAL".equals(mode))throw new ServiceException("直接定制和欠料定制应领用工厂自有料");
            if(stock.get("customer_id")==null||work.get("customer_id")==null||!stock.get("customer_id").toString().equals(work.get("customer_id").toString()))
                throw new ServiceException("来料定制只能领本工单客户的原料",403);
            return "CUSTOMER_INPUT";
        }
        return "MATERIAL".equals(mode)?"FACTORY_SUPPLEMENT":"FACTORY_INPUT";
    }
}
