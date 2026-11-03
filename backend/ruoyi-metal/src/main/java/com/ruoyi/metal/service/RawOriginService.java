package com.ruoyi.metal.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.catalog.RawOriginRequests.Verification;
import com.ruoyi.metal.security.FactoryScope;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(rollbackFor=Exception.class, isolation=Isolation.READ_COMMITTED)
public class RawOriginService {
    private final JdbcTemplate jdbc;
    private final FactoryScope scope;

    public RawOriginService(JdbcTemplate jdbc, FactoryScope scope) { this.jdbc=jdbc; this.scope=scope; }

    public record Origin(String powderOrigin, String washingStatus) {}

    public static Origin receipt(long metalId, String materialType, String powderOrigin) {
        if(metalId<=0 || materialType==null || materialType.isBlank()) throw fail("物料金属或类型缺失，请先核对");
        String origin=powderOrigin==null || powderOrigin.isBlank()?"UNKNOWN":powderOrigin;
        if(!Set.of("NATIVE","RECYCLED","UNKNOWN").contains(origin)) throw fail("粉料来源无效");
        if("NATIVE".equals(origin) && (metalId!=1 || !powder(materialType))) throw fail("只有原生铱粉可免酸洗");
        return new Origin(origin,"NATIVE".equals(origin)?"EXEMPT":"REQUIRED");
    }

    public static void requireMetal(Map<String,Object> inventory, long metalId) {
        Object actual=inventory.get("metal_type_id_snapshot");
        if(actual==null) actual=inventory.get("metal_type_id");
        if(metalId<=0 || number(actual)!=metalId) throw fail("库存金属与工单不一致或尚未核对");
    }

    public static void requireSmelting(Map<String,Object> inventory, long metalId) {
        requireMetal(inventory,metalId);
        if(!"RAW".equals(inventory.get("category"))) throw fail("融定须领用合格原料");
        String washing=text(inventory.get("washing_status"));
        if("PASSED".equals(washing) && !isPowder(inventory)) return;
        if("EXEMPT".equals(washing) && "NATIVE".equals(inventory.get("powder_origin")) && metalId==1 && isPowder(inventory)) return;
        throw fail("原料尚未通过酸洗或来源核对，不能融定");
    }

    public static void requirePostProcess(Map<String,Object> inventory) {
        if(!"SEMI".equals(inventory.get("category"))) throw fail("后加工须领用合格半成品");
        if("PASSED".equals(inventory.get("smelting_status"))) return;
        if(number(inventory.get("origin_processing_policy_version"))>=2
                && "SETTLED".equals(inventory.get("origin_batch_status"))
                && Set.of("SEMI","FINISHED").contains(text(inventory.get("origin_output_stage")))) return;
        throw fail("半成品融定依据尚未核对，不能后加工");
    }

    public String verify(long inventoryId, Verification request) {
        permit();
        if(request==null || inventoryId<=0) throw fail("请选择有效库存及核对内容");
        uuid(request.requestId());
        String fingerprint=hash(inventoryId,request);
        var initial=stock(inventoryId,false);
        long factory=number(initial.get("factory_id"));
        scope.check(factory);
        scope.lockFactories(List.of(factory));
        var inventory=stock(inventoryId,true);
        var prior=jdbc.queryForList("SELECT * FROM metal_inventory_origin_verification WHERE request_id=? FOR UPDATE",request.requestId());
        if(!prior.isEmpty()) {
            var old=prior.get(0);
            if(number(old.get("inventory_id"))!=inventoryId || number(old.get("actor_id"))!=SecurityUtils.getUserId()
                    || !fingerprint.equals(old.get("request_hash"))) throw new ServiceException("相同来源核对请求的内容或操作人不一致",409);
            return old.get("verification_id").toString();
        }
        if(!"0".equals(inventory.get("del_flag"))) throw fail("库存不存在或已归档");
        if(request.version()==null || request.version()<0 || number(inventory.get("version"))!=request.version())
            throw new ServiceException("库存版本已变化，请刷新后重试",409);
        if(request.evidence()==null || request.evidence().isBlank() || request.evidence().length()>500)
            throw fail("请填写不超过500字的核对依据");
        if(!Set.of("RAW","SEMI").contains(text(inventory.get("category")))) throw fail("仅原料及半成品可核对来源资格");
        String origin=request.powderOrigin(),washing=request.washingStatus();
        if(!Set.of("NATIVE","RECYCLED","UNKNOWN").contains(text(origin))
                || !Set.of("EXEMPT","PASSED","REQUIRED","UNVERIFIED").contains(text(washing))) throw fail("来源或酸洗资格无效");
        String smelting=request.smeltingStatus()==null?text(inventory.get("smelting_status")):request.smeltingStatus();
        if(smelting.isEmpty()) smelting="UNVERIFIED";
        if(!Set.of("UNVERIFIED","PASSED").contains(smelting)) throw fail("融定资格无效");
        if("RECYCLED".equals(inventory.get("powder_origin")) && !"RECYCLED".equals(origin)) throw fail("已知回收来源不能改为其他来源");
        long metal=number(inventory.get("metal_type_id_snapshot"));
        if(metal==0) metal=number(inventory.get("metal_type_id"));
        if(metal<=0) throw fail("库存金属缺失，请先核对物料资料");
        if("NATIVE".equals(origin) && (metal!=1 || !isPowder(inventory))) throw fail("只有原生铱粉可免酸洗");
        if("EXEMPT".equals(washing) && (!"NATIVE".equals(origin) || metal!=1 || !isPowder(inventory))) throw fail("只有原生铱粉可免酸洗");
        if("PASSED".equals(washing) && "RAW".equals(inventory.get("category")) && isPowder(inventory)) throw fail("酸洗合格原料应为金属料，不能将粉料直接核对为已酸洗");
        validateBasis(inventory,washing,smelting);

        var login=SecurityUtils.getLoginUser();
        String actor=login==null || login.getUser()==null?null:login.getUser().getNickName();
        if(actor==null || actor.isBlank()) actor="用户#"+SecurityUtils.getUserId();
        Object[] values={inventoryId,factory,request.requestId(),fingerprint,SecurityUtils.getUserId(),actor,request.evidence().strip(),
                inventory.get("powder_origin"),origin,inventory.get("washing_status"),washing,
                inventory.get("smelting_status"),smelting,inventory.get("origin_policy_version"),2,request.version()};
        var key=new GeneratedKeyHolder();
        try {
            jdbc.update(connection->{
                var statement=connection.prepareStatement("INSERT INTO metal_inventory_origin_verification(inventory_id,factory_id,request_id,request_hash,actor_id,actor_name,evidence,old_powder_origin,new_powder_origin,old_washing_status,new_washing_status,old_smelting_status,new_smelting_status,old_origin_policy_version,new_origin_policy_version,inventory_version) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",java.sql.Statement.RETURN_GENERATED_KEYS);
                for(int i=0;i<values.length;i++) statement.setObject(i+1,values[i]);
                return statement;
            },key);
        } catch(DuplicateKeyException duplicate) { throw new ServiceException("来源核对请求编号已被使用，请刷新后重试",409); }
        long verificationId=Objects.requireNonNull(key.getKey()).longValue();
        int changed=jdbc.update("UPDATE metal_inventory SET powder_origin=?,washing_status=?,smelting_status=?,origin_policy_version=2,origin_verification_id=?,version=version+1,update_time=NOW() WHERE inventory_id=? AND version=?",origin,washing,smelting,verificationId,inventoryId,request.version());
        if(changed!=1) throw new ServiceException("库存版本已变化，请刷新后重试",409);
        return Long.toString(verificationId);
    }

    private void validateBasis(Map<String,Object> inventory,String washing,String smelting) {
        boolean washed=false,melted=false;
        if(inventory.get("origin_acid_wash_id")!=null) {
            var acid=one("SELECT factory_id,status FROM metal_acid_wash_batch WHERE acid_wash_id=? FOR UPDATE",inventory.get("origin_acid_wash_id"));
            if(number(acid.get("factory_id"))!=number(inventory.get("factory_id")) || !"SETTLED".equals(acid.get("status"))) throw fail("来源酸洗批次未有效结算，不能核对资格");
            washed=true;
        }
        if(inventory.get("origin_batch_id")!=null) {
            var batch=one("SELECT factory_id,status,processing_policy_version,process_role,output_stage,metal_type_id FROM metal_process_batch WHERE batch_id=? FOR UPDATE",inventory.get("origin_batch_id"));
            if(number(batch.get("factory_id"))!=number(inventory.get("factory_id")) || !"SETTLED".equals(batch.get("status"))) throw fail("来源加工批次未有效结算，不能核对资格");
            if(number(batch.get("processing_policy_version"))>=2) {
                long metal=number(inventory.get("metal_type_id_snapshot"));if(metal==0)metal=number(inventory.get("metal_type_id"));
                if(number(batch.get("metal_type_id"))!=metal) throw fail("来源加工批次金属与库存不一致");
                washed|="ACID_WASH".equals(batch.get("process_role")) && "RAW".equals(batch.get("output_stage"));
                melted=Set.of("SMELTING","POST_PROCESS").contains(text(batch.get("process_role")))
                        && Set.of("SEMI","FINISHED").contains(text(batch.get("output_stage")));
            }
        }
        // 旧库存的书面历史依据保存在独立审计，不补造酸洗报工或融定批次。
        if(number(inventory.get("origin_policy_version"))>=2) {
            if("RAW".equals(inventory.get("category")) && "PASSED".equals(washing) && !washed && !"PASSED".equals(inventory.get("washing_status"))) throw fail("新原料缺少有效酸洗依据");
            if("PASSED".equals(smelting) && !melted && !"PASSED".equals(inventory.get("smelting_status"))) throw fail("新库存缺少有效融定依据");
        }
        if("RAW".equals(inventory.get("category")) && "PASSED".equals(smelting)) throw fail("原料不能核对为已融定半成品");
    }

    public record HistoryContext(List<Map<String,Object>> history,long version) {}

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public List<Map<String,Object>> history(long inventoryId) {
        return historyContext(inventoryId).history();
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public HistoryContext historyContext(long inventoryId) {
        permit();
        var inventory=stock(inventoryId,false);scope.check(number(inventory.get("factory_id")));
        var history=jdbc.queryForList("SELECT CAST(verification_id AS CHAR) id,CAST(inventory_id AS CHAR) inventoryId,request_id requestId,CAST(actor_id AS CHAR) actorId,actor_name actorName,evidence,old_powder_origin oldPowderOrigin,new_powder_origin newPowderOrigin,old_washing_status oldWashingStatus,new_washing_status newWashingStatus,old_smelting_status oldSmeltingStatus,new_smelting_status newSmeltingStatus,old_origin_policy_version oldOriginPolicyVersion,new_origin_policy_version newOriginPolicyVersion,inventory_version inventoryVersion,create_time createTime FROM metal_inventory_origin_verification WHERE inventory_id=? ORDER BY verification_id DESC",inventoryId);
        return new HistoryContext(history,number(inventory.get("version")));
    }

    private Map<String,Object> stock(long id,boolean lock) {
        return one("SELECT i.*,m.material_type,m.metal_type_id FROM metal_inventory i LEFT JOIN metal_material m ON i.category='RAW' AND m.material_id=i.reference_id WHERE i.inventory_id=?"+(lock?" FOR UPDATE":""),id);
    }
    private Map<String,Object> one(String sql,Object...args) {
        var rows=jdbc.queryForList(sql,args);if(rows.size()!=1)throw fail("库存或来源依据不存在");return rows.get(0);
    }
    private void permit() { if(!scope.isAdmin() && !SecurityUtils.hasPermi("metal:inventory:verify-origin")) throw new ServiceException("没有来源核对权限",403); }
    private static boolean powder(String value) { return "粉料".equals(value) || "铱粉".equals(value) || "POWDER".equals(value); }
    private static boolean isPowder(Map<String,Object> inventory) {
        Object kind=inventory.get("raw_material_kind");return kind==null?powder(text(inventory.get("material_type"))):"POWDER".equals(kind);
    }
    private static long number(Object value) { return value==null?0:Long.parseLong(value.toString()); }
    private static String text(Object value) { return value==null?"":value.toString(); }
    private static ServiceException fail(String message) { return new ServiceException(message); }
    private static void uuid(String id) {
        try { if(!UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException(); }
        catch(Exception invalid) { throw fail("来源核对请求编号须为UUID"); }
    }
    private static String hash(long inventoryId,Verification request) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.toJSONString(List.of(inventoryId,request)).getBytes(StandardCharsets.UTF_8))); }
        catch(Exception failure) { throw new IllegalStateException(failure); }
    }
}
