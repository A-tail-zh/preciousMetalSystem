package com.ruoyi.framework.audit;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import java.time.*;
import com.alibaba.fastjson2.*;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;
@Component public class RuntimeCollector {
 private final JdbcTemplate jdbc;
 private record Stamp(long size,long modified){}
 private final Map<Path,Stamp> completedGzip=new java.util.concurrent.ConcurrentHashMap<>();
 private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(RuntimeCollector.class);
 private final Map<Path,Long> retryAfter=new java.util.concurrent.ConcurrentHashMap<>();
 public RuntimeCollector(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @Scheduled(fixedDelay=2000) public void collect(){collect(Path.of("logs"));}
 void collect(Path root){if(!Files.exists(root))return;try(var paths=Files.list(root)){
  for(Path file:paths.filter(p->p.getFileName().toString().startsWith("runtime")&&(p.toString().endsWith(".jsonl")||p.toString().endsWith(".jsonl.gz"))).sorted().toList()){
   long now=System.nanoTime();if(now<retryAfter.getOrDefault(file,Long.MIN_VALUE))continue;
   try{read(file);retryAfter.remove(file);}catch(Exception failure){retryAfter.put(file,now+60_000_000_000L);LOG.warn("RUNTIME_COLLECTION_FILE_FAILED file={} reason={}",file.getFileName(),failure.getClass().getSimpleName());}
  }
 }catch(Exception failure){LOG.warn("RUNTIME_COLLECTION_SCAN_FAILED reason={}",failure.getClass().getSimpleName());}}
 private long cursor(String name){return jdbc.queryForList("SELECT file_offset FROM audit_file_cursor WHERE file_name=?",name).stream().mapToLong(r->((Number)r.get("file_offset")).longValue()).findFirst().orElse(0);}
 private void saveCursor(String name,long offset){jdbc.update("INSERT INTO audit_file_cursor(file_name,file_offset) VALUES(?,?) ON DUPLICATE KEY UPDATE file_offset=VALUES(file_offset)",name,offset);}
 private JSONObject parse(byte[] bytes){
  JSONObject event=JSON.parseObject(new String(bytes,StandardCharsets.UTF_8));
  if(event==null||event.getString("eventId")==null||event.getString("eventId").isBlank())throw new IllegalArgumentException("missing event id");
  if(event.getString("eventId").length()>36||event.getString("level")==null||event.getString("level").isBlank()||event.getString("level").length()>8)throw new IllegalArgumentException("invalid log identity or level");
  var time=LocalDateTime.parse(event.getString("time"));if(time.getYear()<1000||time.getYear()>9999)throw new IllegalArgumentException("unsupported log time");
  for(var entry:Map.of("service",80,"component",160,"traceId",32,"exceptionClass",255).entrySet()){String value=event.getString(entry.getKey());if(value!=null&&value.length()>entry.getValue())throw new IllegalArgumentException("log field exceeds storage limit");}
  return event;
 }
 private void accept(Path file,long start,long end,byte[] bytes)throws Exception{
  JSONObject event;
  try{event=parse(bytes);}catch(RuntimeException invalid){AuditFileQuarantine.preserve(file,start,bytes);saveCursor(file.getFileName().toString(),end);LOG.warn("RUNTIME_LINE_QUARANTINED file={} offset={}",file.getFileName(),start);return;}
  jdbc.update("INSERT IGNORE INTO sys_runtime_log(event_id,event_time,level,service,component,event_type,summary,trace_id,exception_class,detail_json) VALUES(?,?,?,?,?,?,?,?,?,?)",event.get("eventId"),LocalDateTime.parse(event.getString("time")),event.get("level"),event.get("service"),event.get("component"),event.get("exceptionClass")!=null?"EXCEPTION":"RUNTIME",event.get("summary"),event.get("traceId"),event.get("exceptionClass"),JSON.toJSONString(Map.of("stack",Objects.toString(event.get("stack"),""),"thread",Objects.toString(event.get("thread"),""))));
  saveCursor(file.getFileName().toString(),end);
 }
 private void read(Path file)throws Exception{
  String name=file.getFileName().toString();if(name.endsWith(".gz")){readGzip(file);return;}
  long offset=cursor(name);
  try(var in=new RandomAccessFile(file.toFile(),"r")){
   in.seek(offset>in.length()?0:offset);
   for(int i=0;i<300;i++){
    long start=in.getFilePointer();String line=in.readLine();if(line==null)break;long end=in.getFilePointer();
    in.seek(end-1);int last=in.read();in.seek(end);if(last!='\n'&&last!='\r')break;
    accept(file,start,end,line.getBytes(StandardCharsets.ISO_8859_1));
   }
  }
 }
 private void readGzip(Path file)throws Exception{
  Stamp stamp=new Stamp(Files.size(file),Files.getLastModifiedTime(file).toMillis());if(stamp.equals(completedGzip.get(file)))return;
  long offset=cursor(file.getFileName().toString());
  try(var gzip=new BufferedInputStream(openGzip(file))){
   gzip.skipNBytes(offset);
   for(int i=0;i<300;i++){
    var bytes=new ByteArrayOutputStream();int value;
    while((value=gzip.read())!=-1){bytes.write(value);if(value=='\n')break;}
    if(bytes.size()==0){completedGzip.put(file,stamp);break;}
    byte[] raw=bytes.toByteArray();long end=offset+raw.length;
    int length=raw.length;if(length>0&&raw[length-1]=='\n')length--;if(length>0&&raw[length-1]=='\r')length--;
    accept(file,offset,end,Arrays.copyOf(raw,length));offset=end;
   }
  }
 }
 InputStream openGzip(Path file)throws IOException{return new java.util.zip.GZIPInputStream(Files.newInputStream(file));}
}
