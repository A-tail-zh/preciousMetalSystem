package com.ruoyi.metal.service;
public final class BatchOutputQueries {
 private BatchOutputQueries(){}
 public static String actionKey(String alias){if(!alias.matches("[a-zA-Z][a-zA-Z0-9_]*"))throw new IllegalArgumentException("Invalid SQL alias");return "CASE WHEN "+alias+".inbound_submission_id IS NULL THEN CONCAT('D:',"+alias+".order_id) ELSE CONCAT('G:',"+alias+".inbound_submission_id) END";}
}
