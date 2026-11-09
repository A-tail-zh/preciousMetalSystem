package com.ruoyi.framework.audit;

import java.util.*;
import com.alibaba.fastjson2.JSON;
import org.springframework.jdbc.core.JdbcTemplate;

final class TypeManagementAuditSnapshot {
    private final JdbcTemplate jdbc;
    TypeManagementAuditSnapshot(JdbcTemplate jdbc){this.jdbc=jdbc;}
    static boolean handles(String path){return path.matches("/metal/type-management/(metal|product|material|process)(/[0-9]+|/batch-delete)?")||path.matches("/metal/process/types(/[0-9]+)?")||path.matches("/metal/metal-types(/[0-9]+)?");}
    Map<String,Object> snapshot(AuditContext c,Object body,Object result){
        String kind=c.path.startsWith("/metal/process/types")?"process":c.path.startsWith("/metal/metal-types")?"metal":c.path.split("/")[3];
        String table=switch(kind){case "metal"->"metal_type";case "product"->"metal_product_type";case "material"->"metal_material_type";case "process"->"metal_process_type";default->throw new IllegalArgumentException();};
        String pk=kind.equals("metal")?"metal_type_id":kind.equals("process")?"type_id":"id";c.objectType=table;
        if(!Objects.equals(c.actorId,1L)&&c.actorFactory==null)return Map.of();
        Set<Long> ids=new TreeSet<>();String last=c.path.substring(c.path.lastIndexOf('/')+1);
        if(last.matches("[0-9]+"))ids.add(Long.valueOf(last));
        if(body!=null&&!(body instanceof String)){
            var input=JSON.parseObject(JSON.toJSONString(body));var items=input.getJSONArray("items");
            if(items!=null)for(int i=0;i<Math.min(items.size(),100);i++){Long id=items.getJSONObject(i).getLong("id");if(id!=null&&id>0)ids.add(id);}
        }
        Object saved=result instanceof Map<?,?> m?m.get("id"):result;
        if(saved!=null&&saved.toString().matches("[0-9]+"))ids.add(Long.valueOf(saved.toString()));
        Map<String,Object> out=new LinkedHashMap<>();
        for(Long id:ids){
            String sql="SELECT "+pk+" id,factory_id,"+(kind.equals("process")?"type_name name":"name")+","+(kind.equals("material")?"material_category,":"")+"version,create_by,create_time,update_by,update_time FROM "+table+" WHERE "+pk+"=?";
            var rows=Objects.equals(c.actorId,1L)?jdbc.queryForList(sql,id):jdbc.queryForList(sql+" AND factory_id=?",id,c.actorFactory);
            if(rows.size()!=1)continue;var row=new LinkedHashMap<>(rows.get(0));row.put("kind",kind);
            if(row.get("factory_id") instanceof Number factory)c.factories.add(factory.longValue());
            c.objectIds.put("typeId",id);c.businessCode=id.toString();out.put(id.toString(),AuditRedactor.redact(row));
        }
        return out;
    }
}
