package com.ruoyi.framework.audit;
import org.springframework.stereotype.Component;import org.springframework.context.event.EventListener;import org.springframework.context.event.ContextClosedEvent;import org.springframework.boot.context.event.ApplicationReadyEvent;import org.slf4j.*;
@Component public class AuditLifecycle{private final Logger log=LoggerFactory.getLogger(AuditLifecycle.class);@EventListener public void ready(ApplicationReadyEvent e){log.info("SERVICE_STARTED precious-metal");}@EventListener public void stop(ContextClosedEvent e){log.info("SERVICE_STOPPED precious-metal");}}
