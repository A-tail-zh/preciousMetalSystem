package com.ruoyi.metal.service;

import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;

/** Stable global type IDs; adding a label never renumbers existing process records. */
@Service
public class ProcessTypeService {
 private final JdbcTemplate jdbc;
 public ProcessTypeService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public List<Map<String,Object>> list(){return jdbc.queryForList("SELECT CAST(type_id AS CHAR) id,type_name name FROM metal_process_type ORDER BY type_id");}
 public static String validName(String name){
  if(name==null||name.isBlank())throw new ServiceException("请输入工序类型名称");
  String normalized=name.strip();
  if(normalized.length()>100)throw new ServiceException("工序类型名称最多100个字符");
  return normalized;
 }
 public static void requireType(JdbcTemplate jdbc,Integer id){
  if(id==null||id<1||!Integer.valueOf(1).equals(jdbc.queryForObject("SELECT COUNT(*) FROM metal_process_type WHERE type_id=?",Integer.class,id)))throw new ServiceException("工序类型不存在，请重新选择");
 }
 @Transactional(rollbackFor=Exception.class)
 public Map<String,Object> create(String name,String actor){
  String normalized=validName(name);var key=new GeneratedKeyHolder();
  try{jdbc.update(connection->{var statement=connection.prepareStatement("INSERT INTO metal_process_type(type_name,create_by) VALUES(?,?)",Statement.RETURN_GENERATED_KEYS);statement.setString(1,normalized);statement.setString(2,actor);return statement;},key);}
  catch(DuplicateKeyException e){throw new ServiceException("工序类型已存在，请勿重复添加");}
  return Map.of("id",String.valueOf(key.getKey()),"name",normalized);
 }
}
