package com.ruoyi.metal.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.catalog.RawOriginRequests.Verification;
import com.ruoyi.metal.security.FactoryScope;

class RawOriginServiceTest {
    private Map<String,Object> raw() {
        return new HashMap<>(Map.of("category","RAW","metal_type_id",1L,"material_type","粉料",
                "raw_material_kind","POWDER","powder_origin","NATIVE","washing_status","EXEMPT"));
    }

    @Test void onlyNativeIridiumPowderIsExempt() {
        assertEquals(new RawOriginService.Origin("NATIVE","EXEMPT"), RawOriginService.receipt(1L,"粉料","NATIVE"));
        assertEquals("REQUIRED",RawOriginService.receipt(1L,"粉料","RECYCLED").washingStatus());
        assertEquals("REQUIRED",RawOriginService.receipt(2L,"块料",null).washingStatus());
        assertThrows(ServiceException.class,()->RawOriginService.receipt(2L,"粉料","NATIVE"));
        assertThrows(ServiceException.class,()->RawOriginService.receipt(1L,"块料","NATIVE"));
        assertThrows(ServiceException.class,()->RawOriginService.receipt(1L,"粉料","native"));
    }

    @Test void smeltingChecksMetalAndOriginWithoutGuessingLegacyQualification() {
        var stock=raw();assertDoesNotThrow(()->RawOriginService.requireSmelting(stock,1));
        assertThrows(ServiceException.class,()->RawOriginService.requireSmelting(stock,2));
        stock.put("powder_origin","RECYCLED");assertThrows(ServiceException.class,()->RawOriginService.requireSmelting(stock,1));
        stock.remove("washing_status");assertThrows(ServiceException.class,()->RawOriginService.requireSmelting(stock,1));
        stock.put("washing_status","PASSED");stock.put("raw_material_kind","METAL");stock.put("material_type","块料");
        assertDoesNotThrow(()->RawOriginService.requireSmelting(stock,1));
        stock.put("category","SEMI");assertThrows(ServiceException.class,()->RawOriginService.requireSmelting(stock,1));
    }

    @Test void metalSnapshotWinsAndMissingMetalIsNeverAssumed() {
        var stock=raw();stock.put("metal_type_id_snapshot",2L);
        assertThrows(ServiceException.class,()->RawOriginService.requireMetal(stock,1));
        assertDoesNotThrow(()->RawOriginService.requireMetal(stock,2));
        assertThrows(ServiceException.class,()->RawOriginService.requireMetal(Map.of(),1));
    }

    @Test void acidMetalKindWinsOverAPowderMaterialCode() {
        var stock=raw();stock.put("raw_material_kind","METAL");stock.put("washing_status","PASSED");stock.put("powder_origin","UNKNOWN");
        assertDoesNotThrow(()->RawOriginService.requireSmelting(stock,1));
        stock.put("washing_status","EXEMPT");stock.put("powder_origin","NATIVE");
        assertThrows(ServiceException.class,()->RawOriginService.requireSmelting(stock,1));
    }

    @Test void postProcessingRequiresQualifiedSemiStock() {
        var stock=new HashMap<String,Object>(Map.of("category","SEMI","smelting_status","UNVERIFIED"));
        assertThrows(ServiceException.class,()->RawOriginService.requirePostProcess(stock));
        stock.put("smelting_status","PASSED");assertDoesNotThrow(()->RawOriginService.requirePostProcess(stock));
        stock.put("category","RAW");assertThrows(ServiceException.class,()->RawOriginService.requirePostProcess(stock));
    }

    @Test void postProcessingCanUseAnActualSettledV2Origin() {
        assertDoesNotThrow(()->RawOriginService.requirePostProcess(Map.of("category","SEMI",
                "origin_processing_policy_version",2,"origin_output_stage","SEMI","origin_batch_status","SETTLED")));
        assertThrows(ServiceException.class,()->RawOriginService.requirePostProcess(Map.of("category","SEMI",
                "origin_processing_policy_version",2,"origin_output_stage","RAW","origin_batch_status","SETTLED")));
    }

    @Test void replayPrecedesVersionArchiveAndNewValidationButMatchesActorAndContent() throws Exception {
        var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);when(scope.isAdmin()).thenReturn(true);
        var service=new RawOriginService(jdbc,scope);
        var request=new Verification(UUID.randomUUID().toString(),0,"NATIVE","EXEMPT","原始采购凭证");
        var hash=RawOriginService.class.getDeclaredMethod("hash",long.class,Verification.class);hash.setAccessible(true);
        String fingerprint=(String)hash.invoke(null,1L,request);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{
            String sql=call.getArgument(0);
            if(sql.contains("metal_inventory_origin_verification"))return List.of(Map.of("verification_id",9L,"inventory_id",1L,"actor_id",7L,"request_hash",fingerprint));
            if(sql.contains("metal_inventory"))return List.of(Map.of("inventory_id",1L,"factory_id",2L,"del_flag","2","version",99));
            throw new AssertionError(sql);
        });
        try(var security=mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(7L);
            assertEquals("9",service.verify(1L,request));
            verify(scope).lockFactories(List.of(2L));
            verify(jdbc,never()).update(anyString(),any(Object[].class));
            security.when(SecurityUtils::getUserId).thenReturn(8L);
            assertThrows(ServiceException.class,()->service.verify(1L,request));
            security.when(SecurityUtils::getUserId).thenReturn(7L);
            assertThrows(ServiceException.class,()->service.verify(1L,new Verification(request.requestId(),0,"RECYCLED","REQUIRED","改变")));
        }
    }

    @Test void verificationIsNotAvailableToOrdinaryInventoryEditors() {
        var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);
        try(var security=mockStatic(SecurityUtils.class)) {
            security.when(()->SecurityUtils.hasPermi("metal:inventory:verify-origin")).thenReturn(false);
            assertEquals(403,assertThrows(ServiceException.class,()->new RawOriginService(jdbc,scope).verify(1,
                    new Verification(UUID.randomUUID().toString(),0,"NATIVE","EXEMPT","凭证"))).getCode());
            verifyNoInteractions(jdbc);
        }
    }

    @Test void evidenceAndVersionAreValidatedAtServiceBoundary() {
        var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);when(scope.isAdmin()).thenReturn(true);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->call.<String>getArgument(0).contains("metal_inventory_origin_verification")?List.of():List.of(
                Map.of("inventory_id",1L,"factory_id",2L,"category","RAW","version",3,"del_flag","0")));
        var service=new RawOriginService(jdbc,scope);
        assertThrows(ServiceException.class,()->service.verify(1,new Verification(UUID.randomUUID().toString(),0,"NATIVE","EXEMPT","凭证")));
        assertThrows(ServiceException.class,()->service.verify(1,new Verification(UUID.randomUUID().toString(),3,"NATIVE","EXEMPT"," ")));
        assertThrows(ServiceException.class,()->service.verify(1,new Verification(UUID.randomUUID().toString(),3,"NATIVE","EXEMPT","凭".repeat(501))));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }

    private record Fixture(RawOriginService service,JdbcTemplate jdbc,FactoryScope scope,
            Map<String,Object> stock,List<Object> audit) {}

    private Fixture fixture() throws Exception {
        var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);when(scope.isAdmin()).thenReturn(true);
        var stock=new HashMap<String,Object>(Map.of("inventory_id",1L,"factory_id",2L,"category","RAW",
                "version",3,"del_flag","0","metal_type_id_snapshot",1L,"material_type","块料",
                "raw_material_kind","METAL","powder_origin","UNKNOWN","washing_status","UNVERIFIED"));
        stock.put("origin_policy_version",1);stock.put("smelting_status","UNVERIFIED");
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{
            String sql=call.getArgument(0);
            if(sql.contains("metal_inventory_origin_verification"))return List.of();
            if(sql.contains("metal_acid_wash_batch"))return List.of(Map.of("factory_id",2L,"status","SETTLED"));
            if(sql.contains("metal_process_batch"))return List.of(Map.of("factory_id",2L,"status","SETTLED",
                    "processing_policy_version",2,"process_role","SMELTING","output_stage","SEMI","metal_type_id",1L));
            if(sql.contains("metal_inventory"))return List.of(stock);
            throw new AssertionError(sql);
        });
        var audit=new ArrayList<Object>();
        doAnswer(call->{
            var connection=mock(Connection.class);var statement=mock(PreparedStatement.class);
            when(connection.prepareStatement(anyString(),anyInt())).thenReturn(statement);
            doAnswer(set->{audit.add(set.getArgument(1));return null;}).when(statement).setObject(anyInt(),any());
            call.<PreparedStatementCreator>getArgument(0).createPreparedStatement(connection);
            call.<KeyHolder>getArgument(1).getKeyList().add(new HashMap<>(Map.of("GENERATED_KEY",10L)));
            return 1;
        }).when(jdbc).update(any(PreparedStatementCreator.class),any(KeyHolder.class));
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
        return new Fixture(new RawOriginService(jdbc,scope),jdbc,scope,stock,audit);
    }

    @Test void legacyQualificationCreatesAnAuditAndOnlyUpdatesQualificationColumns() throws Exception {
        var f=fixture();var request=new Verification(UUID.randomUUID().toString(),3,"UNKNOWN","PASSED"," 历史酸洗结算凭证 ");
        try(var security=mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(7L);security.when(SecurityUtils::getUsername).thenReturn("origin-tester");
            var user=new SysUser();user.setNickName("来源核对员");user.setUserName("18800000001");
            var login=new LoginUser();login.setUser(user);security.when(SecurityUtils::getLoginUser).thenReturn(login);
            assertEquals("10",f.service.verify(1,request));
            assertEquals(16,f.audit.size());assertEquals("历史酸洗结算凭证",f.audit.get(6));
            assertEquals("UNVERIFIED",f.audit.get(9));assertEquals("PASSED",f.audit.get(10));
            assertEquals(7L,f.audit.get(4));assertEquals("来源核对员",f.audit.get(5));assertEquals(3,f.audit.get(15));
            var sql=org.mockito.ArgumentCaptor.forClass(String.class);
            verify(f.jdbc).update(sql.capture(),eq("UNKNOWN"),eq("PASSED"),eq("UNVERIFIED"),eq(10L),eq(1L),eq(3));
            assertFalse(sql.getValue().contains("net_weight"));assertFalse(sql.getValue().contains("category="));
            assertFalse(sql.getValue().contains("quantity="));
        }
    }

    @Test void newRawStockNeedsARealAcidBasisAndRejectsAnUnsettledAcid() throws Exception {
        var f=fixture();f.stock.put("origin_policy_version",2);
        var request=new Verification(UUID.randomUUID().toString(),3,"UNKNOWN","PASSED","凭证");
        assertThrows(ServiceException.class,()->f.service.verify(1,request));
        assertTrue(f.audit.isEmpty());
        f.stock.put("origin_acid_wash_id",6L);
        doReturn(List.of(Map.of("factory_id",2L,"status","VOID"))).when(f.jdbc)
                .queryForList(contains("metal_acid_wash_batch"),any(Object[].class));
        assertThrows(ServiceException.class,()->f.service.verify(1,request));assertTrue(f.audit.isEmpty());
    }

    @Test void settledLegacyAcidIsARealBasisForNewPolicyMetal() throws Exception {
        var f=fixture();f.stock.put("origin_policy_version",2);f.stock.put("origin_acid_wash_id",6L);f.stock.put("material_type","粉料");
        try(var security=mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(7L);security.when(SecurityUtils::getUsername).thenReturn("origin-tester");
            assertEquals("10",f.service.verify(1,new Verification(UUID.randomUUID().toString(),3,"UNKNOWN","PASSED","旧独立酸洗结算凭证")));
        }
    }

    @Test void recycledPowderCannotBeRelabeledAsNative() throws Exception {
        var f=fixture();f.stock.put("raw_material_kind","POWDER");f.stock.put("material_type","粉料");f.stock.put("powder_origin","RECYCLED");
        assertThrows(ServiceException.class,()->f.service.verify(1,new Verification(UUID.randomUUID().toString(),3,"NATIVE","EXEMPT","凭证")));
        assertThrows(ServiceException.class,()->f.service.verify(1,new Verification(UUID.randomUUID().toString(),3,"UNKNOWN","REQUIRED","中间状态不能清除已知回收来源")));
        assertTrue(f.audit.isEmpty());
    }

    @Test void newSemiNeedsSmeltingBasisWhileLegacySemiCanHaveIndependentEvidence() throws Exception {
        var f=fixture();f.stock.put("category","SEMI");f.stock.put("origin_policy_version",2);
        var request=new Verification(UUID.randomUUID().toString(),3,"UNKNOWN","UNVERIFIED","历史融定凭证","PASSED");
        assertThrows(ServiceException.class,()->f.service.verify(1,request));assertTrue(f.audit.isEmpty());
        f.stock.put("origin_policy_version",1);
        try(var security=mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(7L);security.when(SecurityUtils::getUsername).thenReturn("origin-tester");
            assertEquals("10",f.service.verify(1,request));assertEquals("PASSED",f.audit.get(12));
        }
    }

    @Test void v2SmeltingBasisChecksMetalAndStage() throws Exception {
        var f=fixture();f.stock.put("category","SEMI");f.stock.put("origin_policy_version",2);f.stock.put("origin_batch_id",4L);
        var request=new Verification(UUID.randomUUID().toString(),3,"UNKNOWN","UNVERIFIED","融定结算凭证","PASSED");
        f.stock.put("metal_type_id_snapshot",2L);
        assertThrows(ServiceException.class,()->f.service.verify(1,request));assertTrue(f.audit.isEmpty());
        f.stock.put("metal_type_id_snapshot",1L);
        try(var security=mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(7L);security.when(SecurityUtils::getUsername).thenReturn("origin-tester");
            assertEquals("10",f.service.verify(1,request));
        }
    }

    @Test void factoryScopeIsRequiredEvenForHistoryAndReplay() {
        var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);when(scope.isAdmin()).thenReturn(true);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("factory_id",2L)));
        doThrow(new ServiceException("越权",403)).when(scope).check(2L);
        var service=new RawOriginService(jdbc,scope);
        assertThrows(ServiceException.class,()->service.history(1));
        assertThrows(ServiceException.class,()->service.verify(1,new Verification(UUID.randomUUID().toString(),0,"NATIVE","EXEMPT","凭证")));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
}
