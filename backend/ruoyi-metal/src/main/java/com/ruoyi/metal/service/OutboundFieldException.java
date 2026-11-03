package com.ruoyi.metal.service;
import java.util.*;
public class OutboundFieldException extends RuntimeException {
 private final List<Map<String,String>> fields;
 public OutboundFieldException(List<Map<String,String>> fields){super(fields.isEmpty()?"请核对出库信息":fields.get(0).get("message"));this.fields=List.copyOf(fields);}
 public List<Map<String,String>> fields(){return fields;}
}
