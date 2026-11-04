package com.ruoyi.framework.audit;
import java.nio.file.*;import java.lang.reflect.*;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;
import ch.qos.logback.classic.*;import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.*;import ch.qos.logback.core.util.FileSize;
import ch.qos.logback.core.rolling.helper.TimeBasedArchiveRemover;
import static org.junit.jupiter.api.Assertions.*;
class AuditSettingsTest {
 @TempDir Path root;
 private SizeAndTimeBasedRollingPolicy<ILoggingEvent> attach(LoggerContext context,String name){
  var appender=new RollingFileAppender<ILoggingEvent>();appender.setContext(context);appender.setName(name);appender.setFile(root.resolve(name+".log").toString());
  var policy=new SizeAndTimeBasedRollingPolicy<ILoggingEvent>();policy.setContext(context);policy.setParent(appender);policy.setFileNamePattern(root.resolve(name+".%d{yyyy-MM-dd}.%i.log.gz").toString());policy.setMaxFileSize(FileSize.valueOf("1MB"));policy.setMaxHistory(60);policy.start();appender.setRollingPolicy(policy);context.getLogger(name).addAppender(appender);return policy;
 }
 @Test void refreshUpdatesActualRemoversForRootAndDedicatedLogger()throws Exception{
  var context=new LoggerContext();var runtime=attach(context,org.slf4j.Logger.ROOT_LOGGER_NAME);var login=attach(context,"sys-user");
  Field history=TimeBasedArchiveRemover.class.getDeclaredField("maxHistory");history.setAccessible(true);
  try{for(int days:new int[]{2,180}){AuditSettings.applyFileDays(context,days);for(var p:java.util.List.of(runtime,login)){assertEquals(days,p.getMaxHistory());assertEquals(days,history.get(p.getTimeBasedFileNamingAndTriggeringPolicy().getArchiveRemover()));}}}
  finally{runtime.stop();login.stop();context.stop();}
 }
}
