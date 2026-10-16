package com.ruoyi.framework.audit;
import java.util.*;import java.nio.file.*;import java.io.IOException;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
class AuditFallbackReplayTest {
 @TempDir Path root;
 private static final String GOOD=com.alibaba.fastjson2.JSON.toJSONString(Map.of("event","fixture-event","kind","login","time","2026-10-15T12:00:00"));
 private JdbcTemplate db(long[] offset){var j=mock(JdbcTemplate.class);when(j.queryForList(anyString(),any(Object[].class))).thenAnswer(a->List.of(Map.of("file_offset",offset[0])));when(j.update(contains("INSERT INTO audit_file_cursor"),any(Object[].class))).thenAnswer(a->{offset[0]=((Number)((Object[])a.getRawArguments()[1])[1]).longValue();return 1;});return j;}
 @Test void damagedCompleteLineIsPreservedAndHealthyEventsContinue()throws Exception{
  long[] cursor={0};var j=db(cursor);var d=new AuditDelivery(j,mock(PlatformTransactionManager.class));Path file=root.resolve("audit-fallback.jsonl");Files.writeString(file,"{broken}\n"+GOOD+"\n");d.replayFallback(file);d.replayFallback(file);
  verify(j,times(1)).update(startsWith("INSERT IGNORE INTO audit_outbox"),any(Object[].class));assertEquals(Files.size(file),cursor[0]);try(var files=Files.list(root.resolve("audit-quarantine"))){var isolated=files.toList();assertEquals(1,isolated.size());assertEquals("{broken}",Files.readString(isolated.get(0)));}
 }
 @Test void incompleteTailWaitsForAppendAndDoesNotAdvance()throws Exception{
  long[] cursor={0};var j=db(cursor);var d=new AuditDelivery(j,mock(PlatformTransactionManager.class));Path file=root.resolve("audit-fallback.jsonl");Files.writeString(file,GOOD);d.replayFallback(file);assertEquals(0,cursor[0]);verify(j,never()).update(anyString(),any(Object[].class));Files.writeString(file,"\n",StandardOpenOption.APPEND);d.replayFallback(file);verify(j).update(startsWith("INSERT IGNORE INTO audit_outbox"),any(Object[].class));assertEquals(Files.size(file),cursor[0]);
 }
 @Test void quarantineFailurePreservesCursorAndSource()throws Exception{
  long[] cursor={0};var j=db(cursor);var d=spy(new AuditDelivery(j,mock(PlatformTransactionManager.class)));Path file=root.resolve("audit-fallback.jsonl");Files.writeString(file,"{broken}\n"+GOOD+"\n");doThrow(new IOException("fixture unavailable")).when(d).quarantineFallback(any(),anyLong(),any(byte[].class));d.replayFallback(file);assertEquals(0,cursor[0]);verify(j,never()).update(anyString(),any(Object[].class));assertTrue(Files.readString(file).contains(GOOD));
 }
 @Test void databaseFailureDoesNotQuarantineValidEventOrAdvance()throws Exception{
  long[] cursor={0};var j=db(cursor);var d=new AuditDelivery(j,mock(PlatformTransactionManager.class));Path file=root.resolve("audit-fallback.jsonl");Files.writeString(file,GOOD+"\n");doThrow(new org.springframework.dao.DataAccessResourceFailureException("fixture outage")).when(j).update(startsWith("INSERT IGNORE INTO audit_outbox"),any(Object[].class));d.replayFallback(file);assertEquals(0,cursor[0]);assertFalse(Files.exists(root.resolve("audit-quarantine")));
 }
}
