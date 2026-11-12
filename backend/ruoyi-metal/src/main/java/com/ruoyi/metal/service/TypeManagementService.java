package com.ruoyi.metal.service;

import java.sql.Statement;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.security.FactoryScope;

@Service
public class TypeManagementService {
    public record Write(@Positive Long factoryId, @NotBlank @Size(max=100) String name, @PositiveOrZero Integer version, @Size(max=2) String materialCategory) {}
    public record Item(@NotNull @Positive Long id, @NotNull @PositiveOrZero Integer version) {}
    public record Delete(@Positive Long factoryId, @NotEmpty @Size(max=100) List<@NotNull @Valid Item> items) {}
    public record Page(List<Map<String,Object>> rows, long total) {}
    public enum Kind {
        METAL("metal_type","metal_type_id","metal:metal-type:"),
        PRODUCT("metal_product_type","id","metal:product-type:"),
        MATERIAL("metal_material_type","id","metal:material-type:"),
        PROCESS("metal_process_type","type_id","metal:process-type:");
        public final String table,pk,permission;
        Kind(String table,String pk,String permission){this.table=table;this.pk=pk;this.permission=permission;}
        public String nameColumn(){return this==PROCESS?"type_name":"name";}
        public static Kind of(String kind){return switch(kind){case "metal"->METAL;case "product"->PRODUCT;case "material"->MATERIAL;case "process"->PROCESS;default->throw new ServiceException("无效的类型分类",400);};}
    }
    private final JdbcTemplate jdbc;
    private final FactoryScope scope;
    public TypeManagementService(JdbcTemplate jdbc,FactoryScope scope){this.jdbc=jdbc;this.scope=scope;}
    private static void rejectMovedKinds(Kind k){
        if(k==Kind.PRODUCT)throw new ServiceException("产品类型已改为系统固定配置",400);
        if(k==Kind.PROCESS)throw new ServiceException("工序类型请在工序管理中维护",400);
    }
    private Kind allow(String kind,String action){Kind k=Kind.of(kind);rejectMovedKinds(k);if(!scope.isAdmin()&&!SecurityUtils.hasPermi(k.permission+action))throw new ServiceException("没有当前类型的操作权限",403);return k;}
    public List<String> formulas(){throw new ServiceException("产品类型已改为系统固定配置",400);}
    private String select(Kind k){return "SELECT CAST(t."+k.pk+" AS CHAR) id,t."+k.nameColumn()+" name,"+(k==Kind.MATERIAL?"t.material_category materialCategory,":"")+"CAST(t.factory_id AS CHAR) factoryId,f.factory_name factoryName,t.version,t.create_time createTime,t.update_time updatedAt FROM "+k.table+" t JOIN metal_factory f ON f.factory_id=t.factory_id AND f.del_flag='0'";}
    public Page list(String kind,Long requested,String name,int page,int size){
        Kind k=allow(kind,"list");Long factory=scope.resolveFactory(requested);
        if(page<1||size<1||size>64)throw new ServiceException("分页参数无效",400);
        String filter=name==null?"":name.strip();if(filter.length()>100)throw new ServiceException("类型名称最多100字",400);
        String where=" WHERE t.factory_id=? AND LOCATE(?,t."+k.nameColumn()+")>0";
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM "+k.table+" t"+where,Long.class,factory,filter);
        return new Page(jdbc.queryForList(select(k)+where+" ORDER BY t.update_time DESC,t."+k.pk+" DESC LIMIT ? OFFSET ?",factory,filter,size,((long)page-1)*size),total);
    }
    public Map<String,Object> detail(String kind,Long id,Long requested){
        Kind k=allow(kind,"list");Long factory=scope.resolveFactory(requested);return read(k,id,factory);
    }
    private Map<String,Object> read(Kind k,Long id,Long factory){
        var rows=jdbc.queryForList(select(k)+" WHERE t."+k.pk+"=? AND t.factory_id=?",id,factory);
        if(rows.isEmpty())throw new ServiceException("类型不存在或不属于当前工厂",404);return rows.get(0);
    }
    public static String validName(Kind k,String name){
        String clean=name==null?"":name.strip();if(clean.isEmpty()||clean.length()>100)throw new ServiceException("类型名称须为1至100个字符",400);
        if(k==Kind.PRODUCT&&!ProductTypes.ALL.contains(clean))throw new ServiceException("只能选择已有计算公式对应的产品类型",400);
        return clean;
    }
    private Map<String,Object> lock(Kind k,Long id,Long factory){
        var rows=jdbc.queryForList("SELECT "+k.pk+" id,"+k.nameColumn()+(k==Kind.PROCESS?" name":"")+",version"+(k==Kind.MATERIAL?",material_category materialCategory":"")+" FROM "+k.table+" WHERE "+k.pk+"=? AND factory_id=? FOR UPDATE",id,factory);
        if(rows.isEmpty())throw new ServiceException("类型不存在或不属于当前工厂",404);return rows.get(0);
    }
    static String validCategory(Kind kind,String value){
        if(kind!=Kind.MATERIAL){if(value!=null)throw new ServiceException("仅物料类型支持物料分类",400);return null;}
        if(value==null||!Set.of("旧料","粉料","块料").contains(value))throw new ServiceException("请选择旧料、粉料或块料",400);
        return value;
    }
    private void version(Map<String,Object> row,Integer expected){if(expected==null||((Number)row.get("version")).intValue()!=expected)throw new ServiceException("类型资料已变化，请重新加载后再操作",409);}
    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> create(String kind,Write input){
        Kind k=allow(kind,"add");String name=validName(k,input.name());String category=validCategory(k,input.materialCategory());Long factory=scope.resolveFactory(input.factoryId());scope.lockFactories(List.of(factory));
        var key=new GeneratedKeyHolder();
        try{jdbc.update(connection->{var s=connection.prepareStatement("INSERT INTO "+k.table+"(factory_id,"+k.nameColumn()+",create_by,update_by"+(k==Kind.MATERIAL?",material_category":"")+") VALUES(?,?,?,?"+(k==Kind.MATERIAL?",?":"")+")",Statement.RETURN_GENERATED_KEYS);s.setLong(1,factory);s.setString(2,name);s.setString(3,SecurityUtils.getUsername());s.setString(4,SecurityUtils.getUsername());if(k==Kind.MATERIAL)s.setString(5,category);return s;},key);}
        catch(DuplicateKeyException e){throw new ServiceException("当前工厂已存在同名类型",409);}
        return read(k,Objects.requireNonNull(key.getKey()).longValue(),factory);
    }
    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> edit(String kind,Long id,Write input){
        Kind k=allow(kind,"edit");String name=validName(k,input.name());String category=validCategory(k,input.materialCategory());Long factory=scope.resolveFactory(input.factoryId());scope.lockFactories(List.of(factory));
        var row=lock(k,id,factory);version(row,input.version());
        if(!name.equals(row.get("name"))||(k==Kind.MATERIAL&&row.get("materialCategory")!=null&&!Objects.equals(category,row.get("materialCategory"))))requireUnused(k,id,factory,row.get("name").toString());
        var values=new ArrayList<Object>(List.of(name,SecurityUtils.getUsername()));if(k==Kind.MATERIAL)values.add(category);values.addAll(List.of(id,factory,input.version()));
        try{if(jdbc.update("UPDATE "+k.table+" SET "+k.nameColumn()+"=?,version=version+1,update_by=?,update_time=NOW()"+(k==Kind.MATERIAL?",material_category=?":"")+" WHERE "+k.pk+"=? AND factory_id=? AND version=?",values.toArray())!=1)throw new ServiceException("类型资料已变化，请重新加载",409);}
        catch(DuplicateKeyException e){throw new ServiceException("当前工厂已存在同名类型",409);}
        return read(k,id,factory);
    }
    @Transactional(rollbackFor=Exception.class)
    public void remove(String kind,Delete input){
        Kind k=allow(kind,"remove");Long factory=scope.resolveFactory(input.factoryId());scope.lockFactories(List.of(factory));
        if(input.items()==null||input.items().isEmpty()||input.items().size()>100||input.items().stream().anyMatch(x->x==null||x.id()==null||x.id()<=0||x.version()==null||x.version()<0)||input.items().stream().map(Item::id).distinct().count()!=input.items().size())throw new ServiceException("请选择1至100条不同的有效类型",400);
        var items=input.items().stream().sorted(Comparator.comparing(Item::id)).toList();
        var blocked=new ArrayList<String>();
        for(var item:items){var row=lock(k,item.id(),factory);version(row,item.version());if(referenced(k,item.id(),factory,row.get("name").toString()))blocked.add(row.get("name").toString());}
        if(!blocked.isEmpty())throw new ServiceException("以下类型已被引用，本次未删除任何记录："+String.join("、",blocked),409);
        try{for(var item:items)jdbc.update("DELETE FROM "+k.table+" WHERE "+k.pk+"=? AND factory_id=?",item.id(),factory);}
        catch(org.springframework.dao.DataIntegrityViolationException e){throw new ServiceException("类型已被引用，本次未删除任何记录",409);}
    }
    @Transactional(rollbackFor=Exception.class)
    public void removeLegacyMetal(Long id){
        Kind k=allow("metal","remove");Long factory=scope.catalogFactory("metal",id);scope.lockFactories(List.of(factory));var row=lock(k,id,factory);requireUnused(k,id,factory,row.get("name").toString());
        try{jdbc.update("DELETE FROM metal_type WHERE metal_type_id=? AND factory_id=?",id,factory);}
        catch(org.springframework.dao.DataIntegrityViolationException e){throw new ServiceException("金属类型已被引用，不能删除",409);}
    }
    private void requireUnused(Kind k,Long id,Long factory,String name){if(referenced(k,id,factory,name))throw new ServiceException("类型“"+name+"”已被引用，不能改名、变更分类或删除，请另建类型",409);}
    private boolean referenced(Kind k,Long id,Long factory,String name){
        if(k==Kind.PROCESS)return !jdbc.queryForList("SELECT process_id FROM metal_process WHERE process_type=? LIMIT 1 FOR UPDATE",id).isEmpty();
        if(k==Kind.PRODUCT)return !jdbc.queryForList("SELECT product_id FROM metal_product WHERE factory_id=? AND product_type=? LIMIT 1 FOR UPDATE",factory,name).isEmpty()
            ||jdbc.queryForObject("SELECT COUNT(*) FROM metal_inventory WHERE factory_id=? AND product_type_snapshot=?",Integer.class,factory,name)>0
            ||jdbc.queryForObject("SELECT COUNT(*) FROM metal_saved_quotation WHERE factory_id=? AND JSON_CONTAINS(JSON_EXTRACT(item_snapshot,'$[*].productType'),JSON_QUOTE(?))",Integer.class,factory,name)>0;
        if(k==Kind.MATERIAL)return !jdbc.queryForList("SELECT material_id FROM metal_material WHERE factory_id=? AND material_type=? LIMIT 1 FOR UPDATE",factory,name).isEmpty();
        for(String table:List.of("metal_product","metal_material","metal_type_quote_rule","metal_process_batch"))if(jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE metal_type_id=?",Integer.class,id)>0)return true;
        return jdbc.queryForObject("SELECT COUNT(*) FROM metal_inventory WHERE metal_type_id_snapshot=?",Integer.class,id)>0||jdbc.queryForObject("SELECT COUNT(*) FROM metal_saved_quotation WHERE JSON_CONTAINS(JSON_EXTRACT(item_snapshot,'$[*].metalTypeId'),JSON_QUOTE(?)) OR JSON_CONTAINS(JSON_EXTRACT(item_snapshot,'$[*].metalTypeId'),CAST(? AS JSON))",Integer.class,id.toString(),id.toString())>0;
    }
}
