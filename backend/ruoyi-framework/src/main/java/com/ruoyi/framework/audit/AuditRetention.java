package com.ruoyi.framework.audit;
import java.util.*;import java.time.*;import java.nio.file.*;import java.io.*;import java.security.MessageDigest;import java.util.zip.*;import com.alibaba.fastjson2.JSON;import org.springframework.stereotype.Component;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.transaction.support.TransactionTemplate;import org.springframework.transaction.PlatformTransactionManager;import org.slf4j.*;
@Component public class AuditRetention {
 private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final Path archiveRoot;private static final Logger LOG=LoggerFactory.getLogger(AuditRetention.class);@org.springframework.beans.factory.annotation.Autowired public AuditRetention(JdbcTemplate j,PlatformTransactionManager m){this(j,m,Path.of("logs/audit-archive").toAbsolutePath().normalize());}
 AuditRetention(JdbcTemplate j,PlatformTransactionManager m,Path root){jdbc=j;tx=new TransactionTemplate(m);archiveRoot=root;}
 @Scheduled(fixedDelay=60000,initialDelay=60000) public void run(){runAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));}
 void runAt(LocalDateTime now){LocalDate[] failedDay=new LocalDate[1];try{tx.executeWithoutResult(t->{var config=jdbc.queryForMap("SELECT * FROM audit_log_config WHERE id=1 FOR UPDATE");if(!Boolean.TRUE.equals(config.get("cleanup_enabled"))&&!"1".equals(String.valueOf(config.get("cleanup_enabled"))))return;if(now.toLocalTime().isBefore(LocalTime.of(2,30)))return;
  var unfinished=jdbc.queryForList("SELECT business_date FROM audit_cleanup_history WHERE status IN ('RUNNING','FAILED') ORDER BY business_date LIMIT 1");LocalDate day;
  if(!unfinished.isEmpty())day=LocalDate.parse(unfinished.get(0).get("business_date").toString());else{var last=jdbc.queryForMap("SELECT MAX(business_date) day FROM audit_cleanup_history WHERE status='SUCCESS'");day=last.get("day")==null?now.toLocalDate():LocalDate.parse(last.get("day").toString()).plusDays(1);}if(day.isAfter(now.toLocalDate()))return;failedDay[0]=day;
  var cutoffs=new LinkedHashMap<String,String>();cutoffs.put("OPERATION",day.minusDays(((Number)config.get("operation_days")).intValue()).atStartOfDay().toString());cutoffs.put("LOGIN",day.minusDays(((Number)config.get("login_days")).intValue()).atStartOfDay().toString());cutoffs.put("RUNTIME",day.minusDays(((Number)config.get("runtime_days")).intValue()).atStartOfDay().toString());cutoffs.put("OUTBOX",day.minusDays(7).atStartOfDay().toString());cutoffs.put("ARCHIVE",day.minusDays(((Number)config.get("archive_days")).intValue()).atStartOfDay().toString());
  jdbc.update("INSERT IGNORE INTO audit_cleanup_history(business_date,status,stage,cutoffs_json,summary) VALUES(?,'RUNNING','OPERATION',?,'分批清理开始')",day,JSON.toJSONString(cutoffs));var state=jdbc.queryForMap("SELECT * FROM audit_cleanup_history WHERE business_date=? FOR UPDATE",day);if("SUCCESS".equals(state.get("status")))return;
  if(state.get("cutoffs_json")==null)jdbc.update("UPDATE audit_cleanup_history SET cutoffs_json=? WHERE business_date=?",JSON.toJSONString(cutoffs),day);else cutoffs=JSON.parseObject(state.get("cutoffs_json").toString(),LinkedHashMap.class);
  String stage=state.get("stage").toString();Path root=archiveRoot;FilesSafe.directory(root);int removed=0;String column=null,next=stage;String cutoff=cutoffs.get(stage);
  if(stage.equals("OPERATION")||stage.equals("LOGIN")){long usable=root.toFile().getUsableSpace(),total=root.toFile().getTotalSpace();if(usable<2147483648L||usable<total/10)throw new IllegalStateException("磁盘空间不足，停止归档清理");boolean operation=stage.equals("OPERATION");removed=archiveBatch(operation?"sys_oper_log":"sys_logininfor",operation?"oper_id":"info_id",operation?"oper_time":"login_time",LocalDateTime.parse(cutoff).toLocalDate(),root);column=operation?"operation_count":"login_count";if(removed==0)next=operation?"LOGIN":"RUNTIME";}
  else if(stage.equals("RUNTIME")){removed=jdbc.update("DELETE FROM sys_runtime_log WHERE event_time<? LIMIT 1000",LocalDateTime.parse(cutoff));column="runtime_count";if(removed==0)next="OUTBOX";}
  else if(stage.equals("OUTBOX")){removed=jdbc.update("DELETE FROM audit_outbox WHERE status='DELIVERED' AND create_time<? LIMIT 1000",LocalDateTime.parse(cutoff));column="outbox_count";if(removed==0)next="ARCHIVE";}
  else if(stage.equals("ARCHIVE")){expireArchivesBefore(root,LocalDateTime.parse(cutoff).atZone(ZoneId.of("Asia/Shanghai")).toInstant());next="DONE";}
  else if(!stage.equals("DONE"))throw new IllegalStateException("清理阶段无效");
  String increment=column==null?"":","+column+"="+column+"+?";List<Object> args=new ArrayList<>(List.of(next,next.equals("DONE")?"SUCCESS":"RUNNING",next.equals("DONE")?"全部阶段清理完成":"分批清理进行中"));if(column!=null)args.add(removed);args.add(day);jdbc.update("UPDATE audit_cleanup_history SET stage=?,status=?,summary=?,update_time=NOW()"+increment+" WHERE business_date=?",args.toArray());
 });}catch(Exception error){LOG.warn("AUDIT_CLEANUP_FAILED {}",AuditRedactor.text(error.getMessage()));try{if(failedDay[0]!=null)jdbc.update("INSERT INTO audit_cleanup_history(business_date,status,summary) VALUES(?,'FAILED',?) ON DUPLICATE KEY UPDATE status=IF(status='SUCCESS',status,'FAILED'),summary=IF(status='SUCCESS',summary,VALUES(summary)),update_time=NOW()",failedDay[0],AuditRedactor.text(error.getMessage()));}catch(Exception ignored){}}}
 void archive(String table,String pk,String time,LocalDate cutoff,Path root){
  for(int batch=0;batch<20;batch++)if(archiveBatch(table,pk,time,cutoff,root)==0)return;
 }
 int archiveBatch(String table,String pk,String time,LocalDate cutoff,Path root){
   var rows=jdbc.queryForList("SELECT * FROM "+table+" WHERE "+time+"<? ORDER BY "+pk+" LIMIT 1000 FOR UPDATE",cutoff);
   if(rows.isEmpty())return 0;
   try{
    long size;try(var paths=Files.list(root)){size=paths.mapToLong(p->p.toFile().length()).sum();}
    if(size>5368709120L)throw new IOException("审计归档容量已满");
    List<String> ids=recordIds(rows,pk);
    byte[] data=JSON.toJSONBytes(AuditRedactor.redactComplete(rows));
    String digest=sha256(data),name=table+"-"+UUID.randomUUID();Path file=root.resolve(name+".json.gz");
    try(var zip=new GZIPOutputStream(Files.newOutputStream(file,StandardOpenOption.CREATE_NEW))){zip.write(data);}
    verifyArchive(file,pk,ids,digest);
    Path manifest=root.resolve(name+".manifest.json");
    writeManifest(manifest,Map.of("table",table,"rows",ids.size(),"ids",ids,"sha256",digest,"file",file.getFileName().toString()));
    var saved=JSON.parseObject(Files.readString(manifest));
    if(saved.getIntValue("rows")!=ids.size()||!ids.equals(saved.getList("ids",String.class))||!digest.equals(saved.getString("sha256"))||!file.getFileName().toString().equals(saved.getString("file")))throw new IOException("归档清单校验失败");
    String keys=String.join(",",ids);
    if(table.equals("sys_oper_log"))jdbc.update("DELETE FROM audit_scope WHERE oper_id IN ("+keys+")");
    int removed=jdbc.update("DELETE FROM "+table+" WHERE "+pk+" IN ("+keys+")");
    if(removed!=ids.size())throw new IllegalStateException("归档删除数量不一致，回滚数据库清理");
    return removed;
   }catch(Exception error){throw new IllegalStateException("归档失败，数据库记录保留",error);}
 }
 private static List<String> recordIds(List<? extends Map<String,?>> rows,String pk)throws IOException{
  List<String> ids=new ArrayList<>();for(var row:rows){Object id=row.get(pk);if(id==null||!id.toString().matches("[0-9]+"))throw new IOException("归档记录ID无效");ids.add(id.toString());}
  if(new HashSet<>(ids).size()!=ids.size())throw new IOException("归档记录ID重复");return ids;
 }
 private static String sha256(byte[] data)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}
 void verifyArchive(Path file,String pk,List<String> expected,String digest)throws Exception{
  byte[] restored;try(var zip=new GZIPInputStream(Files.newInputStream(file))){restored=zip.readAllBytes();}
  if(!digest.equals(sha256(restored)))throw new IOException("归档哈希校验失败");
  var records=JSON.parseArray(new String(restored,java.nio.charset.StandardCharsets.UTF_8));
  List<Map<String,?>> rows=new ArrayList<>();for(Object record:records){if(!(record instanceof Map<?,?> map))throw new IOException("归档记录格式无效");rows.add((Map<String,?>)map);}
  if(records.size()!=expected.size()||!new HashSet<>(recordIds(rows,pk)).equals(new HashSet<>(expected)))throw new IOException("归档条数或ID集合校验失败");
 }
 void writeManifest(Path file,Map<String,Object> data)throws IOException{Files.writeString(file,JSON.toJSONString(data),StandardOpenOption.CREATE_NEW);}
 private void expireArchives(Path root,int days){expireArchivesBefore(root,java.time.Instant.now().minus(java.time.Duration.ofDays(days)));}
 private void expireArchivesBefore(Path root,java.time.Instant cutoff){try(var paths=Files.list(root)){for(Path manifest:paths.filter(p->p.getFileName().toString().matches("sys_(oper_log|logininfor)-[a-f0-9-]+[.]manifest[.]json")).toList()){if(!Files.getLastModifiedTime(manifest).toInstant().isBefore(cutoff))continue;var meta=JSON.parseObject(Files.readString(manifest));Path data=root.resolve(meta.getString("file")).normalize();if(!data.getParent().equals(root)||!data.getFileName().toString().matches("sys_(oper_log|logininfor)-[a-f0-9-]+[.]json[.]gz"))continue;if(Files.exists(data)){try(var gzip=new GZIPInputStream(Files.newInputStream(data))){String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(gzip.readAllBytes()));if(!digest.equals(meta.getString("sha256")))continue;}Files.delete(data);}Files.delete(manifest);}}catch(Exception failure){throw new IllegalStateException("过期归档清理失败",failure);}}
 private static class FilesSafe{static void directory(Path path){try{Files.createDirectories(path);}catch(Exception e){throw new IllegalStateException(e);}}}
}
