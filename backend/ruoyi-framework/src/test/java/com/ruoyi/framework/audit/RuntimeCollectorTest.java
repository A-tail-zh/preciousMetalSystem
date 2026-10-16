package com.ruoyi.framework.audit;
import java.nio.file.*;import java.util.*;import java.nio.charset.StandardCharsets;import java.util.zip.GZIPOutputStream;import java.lang.reflect.*;import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;import org.springframework.jdbc.core.JdbcTemplate;import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class RuntimeCollectorTest {
 @TempDir Path root;
 @Test void completedArchiveIsNotDecompressedOnEveryPoll()throws Exception {
  var j=mock(JdbcTemplate.class);when(j.queryForList(anyString(),any(Object[].class))).thenReturn(List.of());var collector=spy(new RuntimeCollector(j));Path file=root.resolve("runtime-test.jsonl.gz");
  String row="{\"eventId\":\"fixture\",\"time\":\"2026-10-15T10:00:00\",\"level\":\"INFO\",\"summary\":\"fixture\"}\n";
  try(var zip=new GZIPOutputStream(Files.newOutputStream(file))){zip.write(row.getBytes(StandardCharsets.UTF_8));}
  Method read=RuntimeCollector.class.getDeclaredMethod("readGzip",Path.class);read.setAccessible(true);read.invoke(collector,file);read.invoke(collector,file);verify(collector,times(1)).openGzip(file);
  Files.setLastModifiedTime(file,java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()+10000));read.invoke(collector,file);verify(collector,times(2)).openGzip(file);
 }

 @Test void corruptArchiveDoesNotBlockHealthyFileAndIsRetriedWithBackoff()throws Exception{
  var j=mock(JdbcTemplate.class);when(j.queryForList(anyString(),any(Object[].class))).thenReturn(List.of());
  var collector=spy(new RuntimeCollector(j));Path bad=root.resolve("runtime.000.jsonl.gz");Files.writeString(bad,"invalid gzip fixture");
  Files.writeString(root.resolve("runtime.jsonl"),"{\"eventId\":\"isolated\",\"time\":\"2026-10-15T10:00:00\",\"level\":\"INFO\",\"thread\":\"test\",\"summary\":\"fixture\"}\n");
  collector.collect(root);collector.collect(root);
  verify(j,times(2)).update(contains("INSERT IGNORE INTO sys_runtime_log"),any(Object[].class));
  verify(collector,times(1)).openGzip(bad);
  verify(j,never()).update(contains("INSERT INTO audit_file_cursor"),eq("runtime.000.jsonl.gz"),anyLong());
 }


 @Test void brokenMiddleLineDoesNotBlockHealthyEventsInSameFile()throws Exception{
  var j=mock(JdbcTemplate.class);when(j.queryForList(anyString(),any(Object[].class))).thenReturn(List.of());var c=new RuntimeCollector(j);Path file=root.resolve("runtime.jsonl");
  Files.writeString(file,"{broken}\n{\"eventId\":\"fixture\",\"time\":\"2026-10-15T10:00:00\",\"level\":\"INFO\",\"thread\":\"test\"}\n");
  c.collect(root);verify(j).update(contains("INSERT IGNORE INTO sys_runtime_log"),any(Object[].class));
 }


 private String event(int index){return com.alibaba.fastjson2.JSON.toJSONString(Map.of("eventId","fixture-"+index,"time","2026-10-15T10:00:00","level","INFO","thread","测试线程"));}
 private JdbcTemplate cursorDb(long[] cursor){var j=mock(JdbcTemplate.class);when(j.queryForList(anyString(),any(Object[].class))).thenAnswer(a->List.of(Map.of("file_offset",cursor[0])));when(j.update(contains("INSERT INTO audit_file_cursor"),any(Object[].class))).thenAnswer(a->{cursor[0]=((Number)((Object[])a.getRawArguments()[1])[1]).longValue();return 1;});return j;}
 @Test void incompleteActiveTailWaitsUntilNewline()throws Exception{
  long[] cursor={0};var j=cursorDb(cursor);var c=new RuntimeCollector(j);Path file=root.resolve("runtime.jsonl");Files.writeString(file,event(1));c.collect(root);assertEquals(0,cursor[0]);verify(j,never()).update(anyString(),any(Object[].class));Files.writeString(file,"\n",StandardOpenOption.APPEND);c.collect(root);assertEquals(Files.size(file),cursor[0]);verify(j).update(contains("INSERT IGNORE INTO sys_runtime_log"),any(Object[].class));
 }
 @Test void gzipResumesAcross300Utf8CrLfRecordsWithoutLosingOrRepeating()throws Exception{
  long[] cursor={0};var j=cursorDb(cursor);var c=new RuntimeCollector(j);Path file=root.resolve("runtime-fixture.jsonl.gz");StringBuilder data=new StringBuilder();for(int i=0;i<301;i++)data.append(event(i)).append("\r\n");byte[] bytes=data.toString().getBytes(StandardCharsets.UTF_8);try(var out=new GZIPOutputStream(Files.newOutputStream(file))){out.write(bytes);}
  c.collect(root);c.collect(root);c.collect(root);assertEquals(bytes.length,cursor[0]);verify(j,times(301)).update(contains("INSERT IGNORE INTO sys_runtime_log"),any(Object[].class));
 }
 @Test void databaseFailureDoesNotAdvanceCursorOrQuarantineHealthyEvent()throws Exception{
  long[] cursor={0};var j=cursorDb(cursor);doThrow(new org.springframework.dao.DataAccessResourceFailureException("fixture failure")).when(j).update(contains("INSERT IGNORE INTO sys_runtime_log"),any(Object[].class));Path file=root.resolve("runtime.jsonl");Files.writeString(file,event(1)+"\n");new RuntimeCollector(j).collect(root);assertEquals(0,cursor[0]);assertFalse(Files.exists(root.resolve("audit-quarantine")));
 }


 @Test void invalidRequiredFieldsAreQuarantinedInsteadOfPoisoningDatabaseRetry()throws Exception{
  long[] cursor={0};var j=cursorDb(cursor);Path file=root.resolve("runtime.jsonl");String bad=com.alibaba.fastjson2.JSON.toJSONString(Map.of("eventId","bad-fixture","time","2026-10-15T10:00:00"));Files.writeString(file,bad+"\n"+event(1)+"\n");new RuntimeCollector(j).collect(root);verify(j).update(contains("INSERT IGNORE INTO sys_runtime_log"),any(Object[].class));assertEquals(Files.size(file),cursor[0]);assertTrue(Files.isDirectory(root.resolve("audit-quarantine")));
 }
}
