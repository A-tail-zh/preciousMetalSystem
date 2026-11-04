package com.ruoyi.framework.audit;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class QuotationAuditTest {
 private JdbcTemplate database(){var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{String sql=call.getArgument(0);if(sql.contains("WHERE request_id"))return List.of(Map.of("id",7L));return List.of(new LinkedHashMap<>(Map.of("quote_id",7L,"factory_id",11L,"quote_no","QT-TEST","item_snapshot","[{\"imageHash\":\"frozen-digest\",\"totalPrice\":\"42.00\"}]")));});return jdbc;}
 private AuditContext context(long actor,long factory){var c=new AuditContext();c.path="/metal/quotation/save";c.verb="POST";c.actorId=actor;c.actorFactory=factory;return c;}
 @Test void aliasesMarkReplaysAndCaptureImageDigestWithoutBinary(){var jdbc=database();var c=context(2,11);var result=new AuditRecorder(jdbc,mock(PlatformTransactionManager.class)).snapshot(c,Map.of("requestId","alias-uuid"),null);assertTrue(c.replayed);assertEquals(Set.of(11L),c.factories);assertEquals("QT-TEST",c.businessCode);assertTrue(result.toString().contains("frozen-digest"));verify(jdbc,never()).queryForList(contains("image_png"),any(Object[].class));}
 @Test void anotherFactoryCannotEnterTheBeforeSnapshot(){var jdbc=database();var c=context(2,22);assertTrue(new AuditRecorder(jdbc,mock(PlatformTransactionManager.class)).snapshot(c,Map.of("requestId","guessed-foreign-uuid"),null).isEmpty());assertTrue(c.factories.isEmpty());}
 @Test void normalizedContentReuseIsAlsoAuditedAsReplay(){var jdbc=database();var c=context(1,11);new AuditRecorder(jdbc,mock(PlatformTransactionManager.class)).snapshot(c,Map.of(),Map.of("id","7","reused",true));assertTrue(c.replayed);assertEquals(Set.of(11L),c.factories);}
}
