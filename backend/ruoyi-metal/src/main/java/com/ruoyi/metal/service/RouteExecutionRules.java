package com.ruoyi.metal.service;
import java.util.*;
import com.ruoyi.common.exception.ServiceException;
/** Immutable sequence-based execution history. Names and current loss rules are not route identity. */
final class RouteExecutionRules {
 record Trace(List<Long> processed,List<Long> skipped,List<Long> newSkipped){}
 static List<Long> skipped(long source,long target){if(source<1||target<=source)throw new ServiceException("来源工序必须早于当前工序");var out=new ArrayList<Long>();for(long n=source+1;n<target;n++)out.add(n);return List.copyOf(out);}
 static Trace advance(List<Long> processed,List<Long> skipped,long source,long target){var added=skipped(source,target);var actual=new ArrayList<>(processed);actual.add(target);var all=new TreeSet<>(skipped);all.addAll(added);return new Trace(List.copyOf(actual),List.copyOf(all),added);}
 static void capacity(long plan,long local,long reserved,long external,long skipped){if(local<0||reserved<0||external<0||skipped<0||local+reserved+external+skipped>plan)throw new ServiceException("本单加工、批次预占、外部接入及许可跳过超过计划数量");}
 static void sameProfile(String current,String incoming){if(current!=null&&!current.equals(incoming))throw new ServiceException("半成品真实加工轨迹不同，请结算或冲销当前批次后分批领用");}
}
