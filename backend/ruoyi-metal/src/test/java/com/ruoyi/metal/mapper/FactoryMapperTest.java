package com.ruoyi.metal.mapper;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FactoryMapperTest {
    private Configuration configuration() throws Exception {
        Configuration c = new Configuration();
        try (InputStream xml = getClass().getResourceAsStream("/mapper/metal/FactoryMapper.xml")) {
            assertNotNull(xml);
            new XMLMapperBuilder(xml, c, "FactoryMapper.xml", c.getSqlFragments()).parse();
        }
        return c;
    }

    private String sql(Configuration c, String method, Map<String, Object> parameters) {
        return c.getMappedStatement(FactoryMapper.class.getName() + "." + method)
            .getBoundSql(parameters).getSql().replaceAll("\\s+", " ").trim();
    }

    @Test void queriesExcludeDeletedFactoriesAndListUsesStableOrder() throws Exception {
        Configuration c = configuration();
        assertTrue(sql(c, "selectList", Map.of()).contains("WHERE del_flag='0' ORDER BY factory_id ASC"));
        assertTrue(sql(c, "selectById", Map.of("id", 3L)).contains("factory_id=? AND del_flag='0'"));
        assertTrue(sql(c, "lockFactory", Map.of("id", 3L)).endsWith("FOR UPDATE"));
    }

    @Test void updateUsesVersionGuardAndDeleteIsLogical() throws Exception {
        Configuration c = configuration();
        var form = new com.ruoyi.metal.dto.FactoryRequest();
        form.setFactoryId(3L);
        form.setVersion(0);
        String update = sql(c, "update", Map.of("form", form, "actor", "tester"));
        assertTrue(update.contains("version=version+1"));
        assertTrue(update.contains("WHERE factory_id=? AND del_flag='0' AND version=?"));
        String delete = sql(c, "delete", Map.of("id", 3L, "actor", "tester"));
        assertTrue(delete.startsWith("UPDATE metal_factory SET del_flag='2'"));
        assertTrue(delete.contains("WHERE factory_id=? AND del_flag='0'"));
    }

    @Test void optionsIncludeOnlyFreeLinesAndCurrentFactoryLines() throws Exception {
        Configuration c = configuration();
        String fresh = sql(c, "selectOptions", Map.of());
        assertTrue(fresh.contains("WHERE factory_id IS NULL"));
        assertFalse(fresh.contains("OR factory_id="));
        String edit = sql(c, "selectOptions", Map.of("factoryId", 3L));
        assertTrue(edit.contains("WHERE factory_id IS NULL OR factory_id=?"));
    }

    @Test void assignmentHasOwnershipGuardAndInsertReturnsGeneratedKey() throws Exception {
        Configuration c = configuration();
        String assign = sql(c, "assignLines", Map.of("ids", List.of(10L, 11L), "factoryId", 3L));
        assertTrue(assign.contains("AND (factory_id IS NULL OR factory_id=?)"));
        var insert = c.getMappedStatement(FactoryMapper.class.getName() + ".insert");
        assertArrayEquals(new String[]{"form.factoryId"}, insert.getKeyProperties());
        assertInstanceOf(org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator.class, insert.getKeyGenerator());
    }

    @Test void mapperParsesAndEmptyLineSelectionProducesValidSql() throws Exception {
        Configuration c = new Configuration();
        try (InputStream xml = getClass().getResourceAsStream("/mapper/metal/FactoryMapper.xml")) {
            new XMLMapperBuilder(xml,c,"FactoryMapper.xml",c.getSqlFragments()).parse();
        }
        String namespace = FactoryMapper.class.getName() + ".";
        String sql = c.getMappedStatement(namespace + "lockLines").getBoundSql(Map.of("ids",List.of())).getSql();
        assertTrue(sql.contains("1=0")); assertFalse(sql.contains("IN")); assertTrue(sql.contains("FOR UPDATE"));
        for (String method : List.of("selectList","selectById","lockFactory","selectLines","selectOptions","lockLines","insert","update","clearLines","assignLines","countLines","delete"))
            assertTrue(c.hasStatement(namespace + method), method);
    }
}
