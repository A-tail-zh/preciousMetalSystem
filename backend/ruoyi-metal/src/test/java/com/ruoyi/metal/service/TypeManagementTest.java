package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.security.FactoryScope;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;

class TypeManagementTest {
    @Test void formulaNamesMatchFieldsAndExecutableCalculations(){
        assertEquals(8,ProductTypes.ALL.size());
        assertEquals(new HashSet<>(ProductTypes.ALL),ProductGeometry.FIELDS.keySet());
        for(String type:ProductTypes.ALL){
            assertEquals(type,TypeManagementService.validName(TypeManagementService.Kind.PRODUCT,type));
            var dimensions=new HashMap<String,String>();
            for(String field:ProductGeometry.FIELDS.get(type))dimensions.put(field,field.startsWith("inner")||field.equals("lowerInnerDiameter")?"2":"4");
            if(type.equals("梯形"))dimensions.put("upperInnerDiameter","2");
            assertTrue(ProductGeometry.volume(type,ProductGeometry.normalize(type,dimensions),new BigDecimal("3.14"),BigDecimal.ONE).signum()>0,type);
        }
    }
    @Test void rejectsAliasesFreeNamesAndUnmappedTables(){
        for(String name:List.of("自定义","杆实心","坩埚平底","铱环","","x".repeat(101)))assertThrows(ServiceException.class,()->TypeManagementService.validName(TypeManagementService.Kind.PRODUCT,name));
        assertThrows(ServiceException.class,()->TypeManagementService.Kind.of("metal_type; DROP TABLE sys_user"));
        assertEquals("铂",TypeManagementService.validName(TypeManagementService.Kind.METAL," 铂 "));
        assertEquals("粉料",TypeManagementService.validName(TypeManagementService.Kind.MATERIAL,"粉料"));
        assertThrows(ServiceException.class,()->TypeManagementService.validName(TypeManagementService.Kind.METAL," "));
    }
    @Test void productQuotationSnapshotBlocksRenameAndDelete(){
        var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);
        when(scope.isAdmin()).thenReturn(true);when(scope.resolveFactory(7L)).thenReturn(7L);
        when(jdbc.queryForList("SELECT id id,name,version FROM metal_product_type WHERE id=? AND factory_id=? FOR UPDATE",10L,7L)).thenReturn(List.of(Map.of("id",10L,"name","环","version",0)));
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(0);
        when(jdbc.queryForObject(contains("metal_saved_quotation"),eq(Integer.class),eq(7L),eq("环"))).thenReturn(1);
        var service=new TypeManagementService(jdbc,scope);
        assertEquals(409,assertThrows(ServiceException.class,()->service.edit("product",10L,new TypeManagementService.Write(7L,"杆",0,null))).getCode());
        assertEquals(409,assertThrows(ServiceException.class,()->service.remove("product",new TypeManagementService.Delete(7L,List.of(new TypeManagementService.Item(10L,0))))).getCode());
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void materialCategoryAcceptsOnlyExactThreeChoices(){
        for(String category:List.of("旧料","粉料","块料"))assertEquals(category,TypeManagementService.validCategory(TypeManagementService.Kind.MATERIAL,category));
        for(String category:Arrays.asList(null,"","铱粉","其他"," 粉料"))assertThrows(ServiceException.class,()->TypeManagementService.validCategory(TypeManagementService.Kind.MATERIAL,category));
        assertNull(TypeManagementService.validCategory(TypeManagementService.Kind.METAL,null));
        assertThrows(ServiceException.class,()->TypeManagementService.validCategory(TypeManagementService.Kind.PRODUCT,"粉料"));
    }
    @Test void referencedMaterialCannotBeRenamedReclassifiedOrDeleted(){
        var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);
        when(scope.isAdmin()).thenReturn(true);when(scope.resolveFactory(7L)).thenReturn(7L);
        when(jdbc.queryForList("SELECT id id,name,version,material_category materialCategory FROM metal_material_type WHERE id=? AND factory_id=? FOR UPDATE",10L,7L)).thenReturn(List.of(Map.of("id",10L,"name","测试料","version",0,"materialCategory","粉料")));
        when(jdbc.queryForList("SELECT material_id FROM metal_material WHERE factory_id=? AND material_type=? LIMIT 1 FOR UPDATE",7L,"测试料")).thenReturn(List.of(Map.of("material_id",1L)));
        var service=new TypeManagementService(jdbc,scope);
        assertEquals(409,assertThrows(ServiceException.class,()->service.edit("material",10L,new TypeManagementService.Write(7L,"测试料",0,"块料"))).getCode());
        assertEquals(409,assertThrows(ServiceException.class,()->service.edit("material",10L,new TypeManagementService.Write(7L,"改名",0,"粉料"))).getCode());
        assertEquals(409,assertThrows(ServiceException.class,()->service.remove("material",new TypeManagementService.Delete(7L,List.of(new TypeManagementService.Item(10L,0))))).getCode());
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void processReferenceBlocksRenameAndAtomicBatchDelete(){
        var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);
        when(scope.isAdmin()).thenReturn(true);when(scope.resolveFactory(7L)).thenReturn(7L);
        for(long id:List.of(10L,11L))when(jdbc.queryForList("SELECT type_id id,type_name name,version FROM metal_process_type WHERE type_id=? AND factory_id=? FOR UPDATE",id,7L)).thenReturn(List.of(Map.of("id",id,"name","打磨"+id,"version",0)));
        when(jdbc.queryForList("SELECT process_id FROM metal_process WHERE process_type=? LIMIT 1 FOR UPDATE",11L)).thenReturn(List.of(Map.of("process_id",1L)));
        var service=new TypeManagementService(jdbc,scope);
        assertEquals(409,assertThrows(ServiceException.class,()->service.edit("process",11L,new TypeManagementService.Write(7L,"清洗",0,null))).getCode());
        assertEquals(409,assertThrows(ServiceException.class,()->service.remove("process",new TypeManagementService.Delete(7L,List.of(new TypeManagementService.Item(10L,0),new TypeManagementService.Item(11L,0))))).getCode());
        assertEquals(409,assertThrows(ServiceException.class,()->service.remove("process",new TypeManagementService.Delete(7L,List.of(new TypeManagementService.Item(10L,1))))).getCode());
        verify(jdbc,never()).update(anyString(),any(Object[].class));
        assertEquals("type_name",TypeManagementService.Kind.of("process").nameColumn());
        assertEquals("打磨",TypeManagementService.validName(TypeManagementService.Kind.PROCESS," 打磨 "));
    }
    @Test void materialPermissionDoesNotGrantTypeWrites(){
        var jdbc=mock(JdbcTemplate.class);var scope=mock(FactoryScope.class);
        try(var security=mockStatic(com.ruoyi.common.utils.SecurityUtils.class)){
            security.when(()->com.ruoyi.common.utils.SecurityUtils.hasPermi("metal:material:add")).thenReturn(true);
            var service=new TypeManagementService(jdbc,scope);
            assertEquals(403,assertThrows(ServiceException.class,()->service.create("material",new TypeManagementService.Write(7L,"新料",null,"粉料"))).getCode());
            assertEquals(403,assertThrows(ServiceException.class,()->service.edit("material",10L,new TypeManagementService.Write(7L,"新料",0,"粉料"))).getCode());
            assertEquals(403,assertThrows(ServiceException.class,()->service.remove("material",new TypeManagementService.Delete(7L,List.of(new TypeManagementService.Item(10L,0))))).getCode());
            verifyNoInteractions(jdbc);
        }
    }
}
