package com.ruoyi.metal.service;

import java.sql.Connection;
import java.util.List;
import com.ruoyi.metal.catalog.ListQuery;
import com.ruoyi.metal.catalog.MaterialRecord;
import com.ruoyi.metal.mapper.MaterialMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** Connection-local tables exercise the real mapper without changing fixture business records. */
@EnabledIfEnvironmentVariable(named="METAL_FLOW_ISOLATED_JDBC_URL",matches=WarehouseIntegrationEnvironment.URL_PATTERN)
class MaterialClassificationLockMapperTest {
    private Connection connection;
    private SqlSession session;
    private MaterialMapper mapper;

    @BeforeEach void setup() throws Exception {
        connection = WarehouseIntegrationEnvironment.dataSource().getConnection();
        execute("CREATE TEMPORARY TABLE metal_type(metal_type_id BIGINT PRIMARY KEY,name VARCHAR(100))");
        execute("CREATE TEMPORARY TABLE metal_material(material_id BIGINT PRIMARY KEY,material_code VARCHAR(64),"
                + "material_type VARCHAR(30),specification VARCHAR(200),metal_type_id BIGINT,create_by VARCHAR(64),"
                + "create_time DATETIME,update_by VARCHAR(64),update_time DATETIME,version INT,del_flag CHAR(1),factory_id BIGINT)");
        execute("CREATE TEMPORARY TABLE metal_inventory(inventory_id BIGINT,reference_id BIGINT,category VARCHAR(16),"
                + "net_weight DECIMAL(20,6),del_flag CHAR(1))");
        execute("CREATE TEMPORARY TABLE metal_warehouse_order_line(line_id BIGINT,reference_id BIGINT,category VARCHAR(16))");
        execute("CREATE TEMPORARY TABLE metal_warehouse_flow(flow_id BIGINT PRIMARY KEY,status VARCHAR(16))");
        execute("CREATE TEMPORARY TABLE metal_warehouse_flow_group(group_id BIGINT PRIMARY KEY,flow_id BIGINT,status VARCHAR(16))");
        execute("CREATE TEMPORARY TABLE metal_warehouse_flow_line(flow_line_id BIGINT,group_id BIGINT,line_json JSON)");
        execute("CREATE TEMPORARY TABLE metal_work_order(work_order_id BIGINT PRIMARY KEY,task_type VARCHAR(24),status VARCHAR(24),del_flag CHAR(1))");
        execute("CREATE TEMPORARY TABLE metal_work_order_item(item_id BIGINT PRIMARY KEY,work_order_id BIGINT,material_id BIGINT)");
        execute("INSERT INTO metal_type VALUES(1,'iridium')");
        execute("INSERT INTO metal_material VALUES(7,'MAT7','粉料','spec',1,'tester',NOW(),'tester',NOW(),0,'0',7)");
        var config = new Configuration(new Environment("material-classification-lock",
                new JdbcTransactionFactory(), new SingleConnectionDataSource(connection, true)));
        try (var xml = getClass().getResourceAsStream("/mapper/metal/MaterialMapper.xml")) {
            assertNotNull(xml);
            new XMLMapperBuilder(xml, config, "MaterialMapper.xml", config.getSqlFragments()).parse();
        }
        session = new SqlSessionFactoryBuilder().build(config).openSession(connection);
        mapper = session.getMapper(MaterialMapper.class);
    }

    @AfterEach void cleanup() throws Exception {
        try {
            if (session != null) session.close();
        } finally {
            if (connection != null) connection.close();
        }
    }

    private void execute(String sql) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void assertLocked(boolean expected) {
        assertEquals(expected, WarehouseReferences.materialReferenced(
                new JdbcTemplate(new SingleConnectionDataSource(connection, true)), 7L), "service reference guard");
        var query = new ListQuery();
        query.setMetalTypeId(1L);
        query.setCode("MAT7");
        List<MaterialRecord> records = List.of(mapper.list(null).get(0),
                mapper.listFiltered(null, query).get(0), mapper.detail(7L), mapper.lock(7L));
        for (var record : records) {
            assertEquals(expected, record.isClassificationLocked(), "classificationLocked for material " + record.getId());
            assertEquals(1L, record.getMetalTypeId());
            assertEquals("iridium", record.getMetalTypeName());
        }
    }

    @ParameterizedTest
    @CsvSource({"WAITING,WAITING,true", "COLLECTING,WAITING,true", "RETURNED,WAITING,true",
            "WAITING,CONFIRMED,true", "CANCELLED,WAITING,false", "VOID,WAITING,false",
            "POSTED,WAITING,false", "WAITING,SUPERSEDED,false", "RETURNED,SUPERSEDED,false"})
    void pendingReferencesFollowFlowAndGroupLifecycle(String flowStatus, String groupStatus, boolean locked) throws Exception {
        execute("INSERT INTO metal_warehouse_flow VALUES(10,'" + flowStatus + "')");
        execute("INSERT INTO metal_warehouse_flow_group VALUES(20,10,'" + groupStatus + "')");
        execute("INSERT INTO metal_warehouse_flow_line VALUES(30,20,'{\"referenceId\":\"7\",\"category\":\"RAW\"}')");
        assertLocked(locked);
    }

    @ParameterizedTest
    @CsvSource({"7,SEMI,false", "7,FINISHED,false", "8,RAW,false", "7,RAW,true"})
    void pendingReferenceRequiresThisMaterialAndRawCategory(String reference, String category, boolean locked) throws Exception {
        execute("INSERT INTO metal_warehouse_flow VALUES(10,'WAITING')");
        execute("INSERT INTO metal_warehouse_flow_group VALUES(20,10,'WAITING')");
        execute("INSERT INTO metal_warehouse_flow_line VALUES(30,20,'{\"referenceId\":" + reference
                + ",\"category\":\"" + category + "\"}')");
        assertLocked(locked);
    }

    @Test void noReferenceLeavesClassificationEditable() {
        assertLocked(false);
    }

    @ParameterizedTest
    @CsvSource({"WAITING_MATERIAL,0,true", "IN_PROGRESS,0,true", "COMPLETED,0,true", "CLOSED_PARTIAL,0,true", "WAITING_MATERIAL,2,false"})
    void rawOrderReferencesProtectBeforeIssuingUntilTheOrderIsDeleted(String status,String deleted,boolean locked)throws Exception {
        execute("INSERT INTO metal_work_order VALUES(40,'RAW_PROCESSING','"+status+"','"+deleted+"')");
        execute("INSERT INTO metal_work_order_item VALUES(50,40,7)");
        assertLocked(locked);
    }

    @Test void anotherMaterialRawPlanDoesNotLockThisMaterial()throws Exception {
        execute("INSERT INTO metal_work_order VALUES(40,'RAW_PROCESSING','WAITING_MATERIAL','0')");
        execute("INSERT INTO metal_work_order_item VALUES(50,40,8)");
        assertLocked(false);
    }

    @Test void zeroBalanceArchivedInventoryStillLocksClassification() throws Exception {
        execute("INSERT INTO metal_inventory VALUES(1,7,'RAW',0,'2')");
        assertLocked(true);
    }

    @Test void historicalPostedLineStillLocksClassification() throws Exception {
        execute("INSERT INTO metal_warehouse_order_line VALUES(1,7,'RAW')");
        assertLocked(true);
    }
}
