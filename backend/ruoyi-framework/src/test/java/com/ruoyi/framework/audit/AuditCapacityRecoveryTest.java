package com.ruoyi.framework.audit;
import java.nio.file.*;import java.time.*;import java.util.*;import java.util.zip.*;import java.security.*;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.transaction.PlatformTransactionManager;
import com.alibaba.fastjson2.JSON;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
class AuditCapacityRecoveryTest {
 @TempDir Path temp;
 private Path manifest(Path root,boolean valid)throws Exception{
  String name="sys_oper_log-00000000-0000-0000-0000-000000000001";byte[] data="[]".getBytes(java.nio.charset.StandardCharsets.UTF_8);Path zip=root.resolve(name+".json.gz");try(var out=new GZIPOutputStream(Files.newOutputStream(zip))){out.write(data);}
  Path manifest=root.resolve(name+".manifest.json");Files.writeString(manifest,JSON.toJSONString(Map.of("file",zip.getFileName().toString(),"sha256",valid?HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)):"invalid")));
  Files.setLastModifiedTime(manifest,java.nio.file.attribute.FileTime.from(Instant.parse("2020-01-01T00:00:00Z")));return manifest;
 }
 @Test void capacityCanBeRecoveredByVerifiedExpiredArchives()throws Exception{
  Path root=temp.toAbsolutePath();Path manifest=manifest(root,true);Path orphan=root.resolve("orphan.txt");Files.writeString(orphan,"keep");
  var j=mock(JdbcTemplate.class);var service=spy(new AuditRetention(j,mock(PlatformTransactionManager.class),root));doReturn(false).when(service).diskSpaceLow(root);doReturn(5368709121L,0L).when(service).archiveBytes(root);
  service.ensureArchiveSpace(root,Instant.parse("2025-01-01T00:00:00Z"));assertFalse(Files.exists(manifest));assertTrue(Files.exists(orphan));verify(j,never()).update(anyString(),any(Object[].class));
 }
 @Test void invalidArchivesAreNotDeletedAndDatabaseIsPreserved()throws Exception{
  Path root=temp.toAbsolutePath();Path manifest=manifest(root,false);var j=mock(JdbcTemplate.class);var service=spy(new AuditRetention(j,mock(PlatformTransactionManager.class),root));doReturn(false).when(service).diskSpaceLow(root);doReturn(5368709121L).when(service).archiveBytes(root);
  assertThrows(IllegalStateException.class,()->service.ensureArchiveSpace(root,Instant.parse("2025-01-01T00:00:00Z")));assertTrue(Files.exists(manifest));assertTrue(Files.exists(root.resolve("sys_oper_log-00000000-0000-0000-0000-000000000001.json.gz")));verify(j,never()).update(anyString(),any(Object[].class));
 }

 @Test void corruptManifestDoesNotPreventOtherVerifiedExpiredFilesFromExpiring()throws Exception{
  Path root=temp.toAbsolutePath();Path good=manifest(root,true);Path bad=root.resolve("sys_oper_log-00000000-0000-0000-0000-000000000000.manifest.json");Files.writeString(bad,"{broken}");Files.setLastModifiedTime(bad,java.nio.file.attribute.FileTime.from(Instant.parse("2020-01-01T00:00:00Z")));
  var service=spy(new AuditRetention(mock(JdbcTemplate.class),mock(PlatformTransactionManager.class),root));doReturn(false).when(service).diskSpaceLow(root);doReturn(5368709121L,0L).when(service).archiveBytes(root);
  assertDoesNotThrow(()->service.ensureArchiveSpace(root,Instant.parse("2025-01-01T00:00:00Z")));assertFalse(Files.exists(good));assertTrue(Files.exists(bad));
 }

}
