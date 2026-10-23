package com.ruoyi.framework.audit;
import java.util.*;
public final class AuditContext {
 public static final ThreadLocal<AuditContext> CURRENT=new ThreadLocal<>();
 public final String trace=UUID.randomUUID().toString().replace("-","");public final String event=UUID.randomUUID().toString();public final long started=System.currentTimeMillis();
 public String failureCode=null;public String path,verb,title="",action="",actor="",actorName="",request="",result="",businessCode="",objectType="";public Long actorId,actorFactory,businessFactory;public boolean logged=false,inside=false,replayed=false;public Map<String,Object> before=Map.of();public Set<Long> factories=new TreeSet<>();
 public boolean httpCaptured=false;public Object snapshotInput;public final Map<String,Object> objectIds=new LinkedHashMap<>();
}
