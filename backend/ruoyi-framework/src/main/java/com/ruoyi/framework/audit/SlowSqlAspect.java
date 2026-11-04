package com.ruoyi.framework.audit;
import org.aspectj.lang.ProceedingJoinPoint;import org.aspectj.lang.annotation.*;import org.springframework.stereotype.Component;import org.springframework.core.annotation.Order;import org.slf4j.*;
@Aspect @Component @Order(300) public class SlowSqlAspect {
 private static final Logger LOG=LoggerFactory.getLogger(SlowSqlAspect.class);
 @Around("execution(public * org.springframework.jdbc.core.JdbcTemplate.*(..))") public Object around(ProceedingJoinPoint p)throws Throwable{long start=System.nanoTime();try{return p.proceed();}finally{long cost=(System.nanoTime()-start)/1000000;if(cost>=AuditSettings.slowSql){String sql=p.getArgs().length>0&&p.getArgs()[0] instanceof String s?s:"prepared statement";LOG.warn("SLOW_SQL {}ms {}",cost,AuditRedactor.text(sql).replaceAll("'[^']*'","?"));}}}
}
