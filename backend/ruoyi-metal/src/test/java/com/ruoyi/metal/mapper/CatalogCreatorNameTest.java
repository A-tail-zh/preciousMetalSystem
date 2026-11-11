package com.ruoyi.metal.mapper;

import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class CatalogCreatorNameTest {
    @ParameterizedTest @ValueSource(strings={"Product", "Line", "Material", "Process"})
    void readsCreatorNameWithoutReplacingAuditAccount(String kind) throws Exception {
        var config = new Configuration();
        String resource = "/mapper/metal/" + kind + "Mapper.xml";
        try (var xml = getClass().getResourceAsStream(resource)) {
            assertNotNull(xml);
            new XMLMapperBuilder(xml, config, resource, config.getSqlFragments()).parse();
        }
        String namespace = "com.ruoyi.metal.mapper." + kind + "Mapper";
        for (String method : new String[]{"list", "listFiltered", "detail", "lock"}) {
            var statement = config.getMappedStatement(namespace + "." + method);
            String sql = statement.getBoundSql(Map.of("id", 7L, "q", new com.ruoyi.metal.catalog.ListQuery())).getSql();
            assertTrue(sql.contains("u.user_name COLLATE utf8mb4_unicode_ci=r.create_by"));
            assertTrue(sql.contains("NULLIF(TRIM(u.nick_name),'')"));
            assertTrue(sql.contains("THEN '管理员'"));
            assertTrue(sql.contains("'人员资料不可用') creator_name"));
            var mappings = statement.getResultMaps().get(0).getResultMappings();
            var record = statement.getResultMaps().get(0).getType().getDeclaredConstructor().newInstance();
            var meta = config.newMetaObject(record);
            meta.setValue("creatorName", "管理员");
            meta.setValue("createBy", "admin");
            assertEquals("管理员", meta.getValue("creatorName"));
            assertEquals("admin", meta.getValue("createBy"));
            assertTrue(mappings.stream().anyMatch(m -> "creatorName".equals(m.getProperty()) && "creator_name".equals(m.getColumn())));
            assertTrue(mappings.stream().anyMatch(m -> "createBy".equals(m.getProperty()) && "create_by".equals(m.getColumn())));
        }
    }
}
