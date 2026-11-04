package com.ruoyi.metal.mapper;
import java.util.*;
import org.apache.ibatis.annotations.Param;
/**
 * 生产工单数据访问接口，具体查询及写入规则由 MyBatis 映射文件定义。
 */
public interface WorkOrderMapper {List<Map<String,Object>> list(@Param("scopeId") Long scopeId);}
