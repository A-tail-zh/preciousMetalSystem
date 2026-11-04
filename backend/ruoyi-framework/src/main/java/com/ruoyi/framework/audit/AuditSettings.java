package com.ruoyi.framework.audit;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.LoggerFactory;
@Component public class AuditSettings {
 public static volatile long slowRequest=2000,slowSql=1000;
 private final JdbcTemplate jdbc;
 public AuditSettings(JdbcTemplate j){jdbc=j;}
 @Scheduled(fixedDelay=10000) public void refresh(){try{
  var c=jdbc.queryForMap("SELECT * FROM audit_log_config WHERE id=1");
  slowRequest=((Number)c.get("slow_request_ms")).longValue();slowSql=((Number)c.get("slow_sql_ms")).longValue();
  applyFileDays((ch.qos.logback.classic.LoggerContext)LoggerFactory.getILoggerFactory(),((Number)c.get("file_days")).intValue());
 }catch(Exception failure){LoggerFactory.getLogger(AuditSettings.class).warn("AUDIT_SETTINGS_REFRESH_FAILED {}",failure.getClass().getSimpleName());}}
 static void applyFileDays(ch.qos.logback.classic.LoggerContext context,int days){
  Set<Object> visited=Collections.newSetFromMap(new IdentityHashMap<>());
  for(var logger:context.getLoggerList()){var appenders=logger.iteratorForAppenders();while(appenders.hasNext()){
   var appender=appenders.next();if(appender instanceof ch.qos.logback.core.rolling.RollingFileAppender<?> rolling&&rolling.getRollingPolicy() instanceof ch.qos.logback.core.rolling.TimeBasedRollingPolicy<?> policy&&visited.add(policy)){
    synchronized(policy){policy.setMaxHistory(days);var trigger=policy.getTimeBasedFileNamingAndTriggeringPolicy();if(trigger!=null&&trigger.getArchiveRemover()!=null)trigger.getArchiveRemover().setMaxHistory(days);}
   }
  }}
 }
}
