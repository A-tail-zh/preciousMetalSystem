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
 private final com.ruoyi.metal.security.FactoryScope scope;
 public ProcessTypeService(JdbcTemplate jdbc){this(jdbc,new com.ruoyi.metal.security.FactoryScope(jdbc,null));}
 @org.springframework.beans.factory.annotation.Autowired public ProcessTypeService(JdbcTemplate jdbc,com.ruoyi.metal.security.FactoryScope scope){this.jdbc=jdbc;this.scope=scope;}
 public List<Map<String,Object>> list(){return list(null);}
 public List<Map<String,Object>> list(Long requested){Long factory=scope.resolveFactory(requested);return jdbc.queryForList("SELECT CAST(type_id AS CHAR) id,type_name name,CAST(factory_id AS CHAR) factoryId FROM metal_process_type WHERE factory_id=? ORDER BY type_id",factory);}
 @Transactional(rollbackFor=Exception.class)
 public void remove(Long id){
  Long factory=scope.catalogFactory("process-type",id);scope.lockFactories(List.of(factory));
  if(jdbc.queryForList("SELECT type_id FROM metal_process_type WHERE type_id=? AND factory_id=? FOR UPDATE",id,factory).isEmpty())throw new ServiceException("工序类型不存在或已删除");
  if(jdbc.queryForObject("SELECT COUNT(*) FROM metal_process WHERE process_type=?",Integer.class,id)>0)throw new ServiceException("工序类型已被工序引用，不能删除");
  try{if(jdbc.update("DELETE FROM metal_process_type WHERE type_id=? AND factory_id=?",id,factory)!=1)throw new ServiceException("工序类型不存在或已删除");}
  catch(org.springframework.dao.DataIntegrityViolationException e){throw new ServiceException("工序类型已被引用，不能删除");}
 }
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
 public Map<String,Object> create(String name,String actor){return create(name,actor,null);}
 @Transactional(rollbackFor=Exception.class)
 public Map<String,Object> create(String name,String actor,Long requested){
  Long factory=scope.resolveFactory(requested);scope.lockFactories(List.of(factory));
  String normalized=validName(name);var key=new GeneratedKeyHolder();
  try{jdbc.update(connection->{var statement=connection.prepareStatement("INSERT INTO metal_process_type(factory_id,type_name,create_by) VALUES(?,?,?)",Statement.RETURN_GENERATED_KEYS);statement.setLong(1,factory);statement.setString(2,normalized);statement.setString(3,actor);return statement;},key);}
  catch(DuplicateKeyException e){throw new ServiceException("工序类型已存在，请勿重复添加");}
  return Map.of("id",String.valueOf(key.getKey()),"name",normalized);
 }
}
