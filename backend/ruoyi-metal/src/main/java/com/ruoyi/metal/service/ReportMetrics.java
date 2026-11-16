package com.ruoyi.metal.service;

import java.math.*;
import java.util.*;

/** Read-only metrics: never replace historical report thresholds with live product settings. */
final class ReportMetrics {
    private ReportMetrics() {}
    static String percentage(BigDecimal loss, BigDecimal net, boolean reliable) {
        if (!reliable || loss == null || net == null || net.signum() <= 0 || loss.signum() < 0 || loss.compareTo(net) > 0) return null;
        return loss.multiply(BigDecimal.valueOf(100)).divide(net, 2, RoundingMode.HALF_UP).toPlainString();
    }
    static Map<String,Object> thresholds(List<?> values) {
        var result=new LinkedHashMap<String,Object>();
        for (String key:List.of("thresholdValue","thresholdMin","thresholdMax","thresholdWarning")) result.put(key,null);
        if (values.isEmpty()) return result;
        if (values.stream().anyMatch(Objects::isNull)) { result.put("thresholdWarning","历史预警值缺失，请查看批次核对"); return result; }
        var numbers=values.stream().map(v->new BigDecimal(v.toString())).toList();
        if (numbers.stream().anyMatch(v->v.signum()<0)) { result.put("thresholdWarning","历史预警值异常，请查看批次核对"); return result; }
        var min=Collections.min(numbers);var max=Collections.max(numbers);
        result.put("thresholdMin",min.setScale(2,RoundingMode.HALF_UP).toPlainString());result.put("thresholdMax",max.setScale(2,RoundingMode.HALF_UP).toPlainString());
        if (min.compareTo(max)==0) result.put("thresholdValue",min.setScale(2,RoundingMode.HALF_UP).toPlainString());
        else result.put("thresholdWarning","历史批次预警值不一致，请查看批次");
        return result;
    }
}
