package com.ruoyi.metal.service;

import java.util.*;
import java.sql.Statement;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.security.FactoryScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.ruoyi.metal.service.StorageLedgerService.*;

@Service
@Transactional(rollbackFor=Exception.class)
public class StorageWarehouseService {
    private final JdbcTemplate jdbc;private final FactoryScope scope;
    public StorageWarehouseService(JdbcTemplate jdbc,FactoryScope scope){this.jdbc=jdbc;this.scope=scope;}
    void allow(String action){if(!scope.isAdmin()&&!SecurityUtils.getLoginUser().getPermissions().contains("metal:warehouse:"+action)&&!SecurityUtils.getLoginUser().getPermissions().contains("*:*:*"))throw new ServiceException("没有此操作权限",403);}
    Map<String,Object> one(long factory,long warehouse,boolean lock){var rows=jdbc.queryForList("SELECT w.*,f.factory_name FROM metal_warehouse w JOIN metal_factory f ON f.factory_id=w.factory_id WHERE w.warehouse_id=? AND w.factory_id=? AND w.del_flag='0'"+(lock?" FOR UPDATE":""),warehouse,factory);if(rows.isEmpty())throw fail("仓库不存在或不属于当前工厂");return rows.get(0);}
    Map<String,Object> row(Map<String,Object> value){var out=api(value);out.put("id",text(value.get("warehouse_id")));return out;}
    public Map<String,Object> list(Long requested,String name,String status,int page,int size){allow("list");long factory=scope.resolveFactory(requested);if(page<1||size<1||size>500)throw fail("分页参数无效");String where="w.factory_id=? AND w.del_flag='0'";var args=new ArrayList<Object>();args.add(factory);if(name!=null&&!name.isBlank()){where+=" AND w.name LIKE ?";args.add("%"+name.trim()+"%");}if(status!=null&&!status.isBlank()){if(!Set.of("0","1").contains(status))throw fail("状态无效");where+=" AND w.status=?";args.add(status);}long total=jdbc.queryForObject("SELECT COUNT(*) FROM metal_warehouse w WHERE "+where,Long.class,args.toArray());args.add(size);args.add((page-1)*size);return Map.of("rows",jdbc.queryForList("SELECT w.*,f.factory_name FROM metal_warehouse w JOIN metal_factory f ON f.factory_id=w.factory_id WHERE "+where+" ORDER BY w.sort_order,w.warehouse_id LIMIT ? OFFSET ?",args.toArray()).stream().map(this::row).toList(),"total",total,"code",200);}
    public Map<String,Object> detail(Long requested,long warehouse){if(!scope.isAdmin()&&!SecurityUtils.getLoginUser().getPermissions().contains("metal:warehouse:list"))allow("edit");return row(one(scope.resolveFactory(requested),warehouse,false));}
    public List<Map<String,Object>> options(Long requested){long factory=scope.resolveFactory(requested);return jdbc.queryForList("SELECT warehouse_id,name,factory_id,version FROM metal_warehouse WHERE factory_id=? AND status='0' AND del_flag='0' ORDER BY sort_order,warehouse_id",factory).stream().map(this::row).toList();}
    long factory(Map<String,Object> input){return scope.resolveFactory(input.get("factoryId")==null?null:id(input.get("factoryId")));}
    String request(Map<String,Object> input){String request=text(input.get("requestId"));try{UUID.fromString(request);}catch(Exception e){throw fail("提交标识无效");}return request.toLowerCase(Locale.ROOT);}
    Map<String,Object> replay(long factory,String request,String hash){var rows=jdbc.queryForList("SELECT request_hash,result FROM metal_storage_warehouse_request WHERE factory_id=? AND actor_id=? AND request_id=? FOR UPDATE",factory,SecurityUtils.getUserId(),request);if(rows.isEmpty())return null;if(!hash.equals(rows.get(0).get("request_hash")))throw fail("同一提交标识内容已改变");return JSON.parseObject(text(rows.get(0).get("result")));}
    Map<String,Object> done(long factory,String request,String hash,Map<String,Object> result){jdbc.update("INSERT INTO metal_storage_warehouse_request(factory_id,actor_id,request_id,request_hash,result) VALUES(?,?,?,?,?)",factory,SecurityUtils.getUserId(),request,hash,JSON.toJSONString(result));return result;}
    public Map<String,Object> operation(Long requested,String request){long factory=scope.resolveFactory(requested);var rows=jdbc.queryForList("SELECT result FROM metal_storage_warehouse_request WHERE factory_id=? AND actor_id=? AND request_id=?",factory,SecurityUtils.getUserId(),request);if(rows.isEmpty())throw new ServiceException("尚未查到此次操作",404);return JSON.parseObject(text(rows.get(0).get("result")));}
    public Map<String,Object> save(Long warehouse,Map<String,Object> input){allow(warehouse==null?"add":"edit");long factory=factory(input);scope.lockFactories(List.of(factory));String request=request(input),digest=hash(Map.of("action",warehouse==null?"add":"edit","warehouseId",warehouse==null?0:warehouse,"input",input));var old=replay(factory,request,digest);if(old!=null)return old;String name=text(input.get("name")).trim(),status=text(input.getOrDefault("status","0"));long sort=qty(input.getOrDefault("sortOrder",0));if(name.isEmpty()||name.length()>100||!Set.of("0","1").contains(status))throw fail("仓库名称或状态无效");if(warehouse!=null){var current=one(factory,warehouse,true);if(id(input.get("version"))!=id(current.get("version")))throw fail("仓库已被修改，请重新打开核对");}
        if(!jdbc.queryForList("SELECT warehouse_id FROM metal_warehouse WHERE factory_id=? AND name=? AND warehouse_id<>?",factory,name,warehouse==null?0:warehouse).isEmpty())throw fail("本厂已存在同名仓库（含历史仓库）");
        if(warehouse==null){var key=new GeneratedKeyHolder();jdbc.update(c->{var statement=c.prepareStatement("INSERT INTO metal_warehouse(factory_id,name,sort_order,status,create_by) VALUES(?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);statement.setObject(1,factory);statement.setObject(2,name);statement.setObject(3,sort);statement.setObject(4,status);statement.setObject(5,SecurityUtils.getUsername());return statement;},key);warehouse=Objects.requireNonNull(key.getKey()).longValue();}else jdbc.update("UPDATE metal_warehouse SET name=?,sort_order=?,status=?,version=version+1,update_time=NOW() WHERE warehouse_id=? AND factory_id=?",name,sort,status,warehouse,factory);
        return done(factory,request,digest,row(one(factory,warehouse,false)));
    }
    public Map<String,Object> delete(Map<String,Object> input){allow("remove");long factory=factory(input);scope.lockFactories(List.of(factory));String request=request(input),digest=hash(Map.of("action","remove","input",input));var old=replay(factory,request,digest);if(old!=null)return old;var targets=StorageLedgerService.list(input.get("rows"));Set<Long> unique=new HashSet<>();for(var target:targets){long warehouse=id(target.get("id"));if(!unique.add(warehouse))throw fail("仓库重复");var current=one(factory,warehouse,true);if(id(target.get("version"))!=id(current.get("version")))throw fail("仓库已被修改，请重新选择");
            if(!jdbc.queryForList("SELECT id FROM metal_storage_stock WHERE warehouse_id=? AND factory_id=? LIMIT 1",warehouse,factory).isEmpty()||!jdbc.queryForList("SELECT inventory_id FROM metal_inventory WHERE warehouse_id=? LIMIT 1",warehouse).isEmpty()||!jdbc.queryForList("SELECT order_id FROM metal_warehouse_order WHERE warehouse_id=? LIMIT 1",warehouse).isEmpty())throw fail("仓库已被库存或单据引用，仅允许停用");
            // New document references are validated even before any stock has posted.
            if(!jdbc.queryForList("SELECT l.id FROM metal_storage_document_line l JOIN metal_storage_document d ON d.id=l.document_id WHERE d.factory_id=? AND CAST(JSON_UNQUOTE(JSON_EXTRACT(l.data,'$.warehouseId')) AS UNSIGNED)=? LIMIT 1",factory,warehouse).isEmpty())throw fail("仓库已被仓储单据引用，仅允许停用");
        }for(long warehouse:unique)jdbc.update("UPDATE metal_warehouse SET del_flag='2',version=version+1,update_time=NOW() WHERE warehouse_id=? AND factory_id=?",warehouse,factory);return done(factory,request,digest,Map.of("deleted",true,"count",unique.size()));}
}
