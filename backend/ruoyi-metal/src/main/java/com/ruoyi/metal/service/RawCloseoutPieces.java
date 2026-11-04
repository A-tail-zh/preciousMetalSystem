package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.*;
import com.ruoyi.common.exception.ServiceException;

/** Allocates real RAW piece balances once per inventory, independently of residual-lot weights. */
public final class RawCloseoutPieces {
    private RawCloseoutPieces() {}

    public static Map<Long,Long> allocate(List<Map<String,Object>> stocks,
                                         Map<Long,BigDecimal> selectedWeights,Map<Long,Long> requested) {
        if(stocks==null||selectedWeights==null)throw invalid("余料库存或选择资料不完整");
        var byId=new LinkedHashMap<Long,Map<String,Object>>();
        for(var stock:stocks){
            Object value=stock.get("inventory_id");
            if(value==null)value=stock.get("inventoryId");
            if(value==null)value=stock.get("id");
            if(value==null)throw invalid("余料库存编号缺失，请刷新后重试");
            byId.putIfAbsent(Long.valueOf(value.toString()),stock);
        }
        if(requested!=null)for(var id:requested.keySet()){
            var stock=byId.get(id);
            if(!selectedWeights.containsKey(id)||stock==null||!"RAW".equals(stock.get("category"))||InventoryMeasurement.weight(stock))
                throw invalid("件数只能填写本次选中的按件原料库存");
        }
        var result=new LinkedHashMap<Long,Long>();
        for(var entry:selectedWeights.entrySet()){
            Long id=entry.getKey();var stock=byId.get(id);BigDecimal selected=entry.getValue();
            if(stock==null||!"RAW".equals(stock.get("category")))throw invalid("选中的余料库存不存在或分类不符");
            BigDecimal available=decimal(stock.get("available_weight")!=null?stock.get("available_weight"):stock.get("net_weight"));
            if(selected==null||selected.signum()<=0||selected.compareTo(available)>0)throw invalid("余料收尾净重须大于0且不能超过库存可用净重");
            if(InventoryMeasurement.weight(stock)){result.put(id,0L);continue;}
            Object count=stock.get("available_quantity")!=null?stock.get("available_quantity"):stock.get("quantity");
            long availableQuantity=count==null?0:Long.parseLong(count.toString());
            boolean allWeight=selected.compareTo(available)==0;
            Long quantity=requested==null?(allWeight?availableQuantity:null):requested.get(id);
            if(quantity==null||quantity<=0||quantity>availableQuantity)throw invalid("按件原料部分收尾须填写正件数，且不能超过库存可用件数");
            if((quantity==availableQuantity)!=allWeight)throw invalid("全部件数与全部净重须同时取完，不能只清空其中一项");
            result.put(id,quantity);
        }
        return result;
    }

    private static BigDecimal decimal(Object value){return value==null?BigDecimal.ZERO:new BigDecimal(value.toString());}
    private static ServiceException invalid(String message){return new ServiceException(message);}
}
