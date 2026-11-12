package com.ruoyi.metal.service;

import java.sql.Statement;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;

/** Stable global type IDs; adding a label never renumbers existing process records. */
@Service
public class ProcessTypeService {
 private static final Set<String> RESERVED=Set.of("酸洗","融定");
 private final JdbcTemplate jdbc;
 private final com.ruoyi.metal.security.FactoryScope scope;
 public ProcessTypeService(JdbcTemplate jdbc){this(jdbc,new com.ruoyi.metal.security.FactoryScope(jdbc,null));}
 @org.springframework.beans.factory.annotation.Autowired public ProcessTypeService(JdbcTemplate jdbc,com.ruoyi.metal.security.FactoryScope scope){this.jdbc=jdbc;this.scope=scope;}
 public List<Map<String,Object>> list(){return list(null);}
 public List<Map<String,Object>> list(Long requested){Long factory=scope.resolveFactory(requested);return jdbc.queryForList("SELECT CAST(type_id AS CHAR) id,type_name name,builtin_code builtinCode,version,CAST(factory_id AS CHAR) factoryId FROM metal_process_type WHERE factory_id=? ORDER BY type_id",factory);}
 private Map<String,Object> read(long id,long factory){
  var rows=jdbc.queryForList("SELECT CAST(type_id AS CHAR) id,type_name name,builtin_code builtinCode,version,CAST(factory_id AS CHAR) factoryId FROM metal_process_type WHERE type_id=? AND factory_id=?",id,factory);
  if(rows.isEmpty())throw new ServiceException("工序类型不存在或不属于当前工厂",404);return rows.get(0);
 }
 private Map<String,Object> lock(long id,long factory){
  var rows=jdbc.queryForList("SELECT type_id id,type_name name,builtin_code builtinCode,version FROM metal_process_type WHERE type_id=? AND factory_id=? FOR UPDATE",id,factory);
  if(rows.isEmpty())throw new ServiceException("工序类型不存在或不属于当前工厂",404);return rows.get(0);
 }
 private static void version(Map<String,Object> row,Integer expected){if(expected==null||((Number)row.get("version")).intValue()!=expected)throw new ServiceException("工序类型资料已变化，请重新加载后再操作",409);}
 private static boolean builtin(Map<String,Object> row){return row.get("builtinCode")!=null&&!Objects.toString(row.get("builtinCode"),"").isBlank();}
 private void requireUnused(long id,String name){if(jdbc.queryForObject("SELECT COUNT(*) FROM metal_process WHERE process_type=?",Integer.class,id)>0)throw new ServiceException("工序类型“"+name+"”已被引用，不能改名或删除，请另建类型",409);}
 public static String validName(String name){
  if(name==null||name.isBlank())throw new ServiceException("请输入工序类型名称");
  String normalized=name.strip();
  if(normalized.length()>100)throw new ServiceException("工序类型名称最多100个字符");
  if(RESERVED.contains(normalized))throw new ServiceException("酸洗、融定为系统内置类型，请直接选用");
  return normalized;
 }
 public static void requireType(JdbcTemplate jdbc,Integer id){
  if(id==null||id<1||!Integer.valueOf(1).equals(jdbc.queryForObject("SELECT COUNT(*) FROM metal_process_type WHERE type_id=?",Integer.class,id)))throw new ServiceException("工序类型不存在，请重新选择");
 }
 public static String semantic(JdbcTemplate jdbc,Integer typeId,long factory){
  if(typeId==null)throw new ServiceException("请选择工序类型");
  var rows=jdbc.queryForList("SELECT builtin_code FROM metal_process_type WHERE type_id=? AND factory_id=?",typeId,factory);
  if(rows.isEmpty())throw new ServiceException("工序类型不存在或不属于本厂");
  Object code=rows.get(0).get("builtin_code");
  return code==null?"NORMAL":code.toString();
 }
 @Transactional(rollbackFor=Exception.class)
 public Map<String,Object> create(String name,String actor){return create(name,actor,null);}
 @Transactional(rollbackFor=Exception.class)
 public Map<String,Object> create(String name,String actor,Long requested){
  Long factory=scope.resolveFactory(requested);scope.lockFactories(List.of(factory));
  String normalized=validName(name);var key=new GeneratedKeyHolder();
  String by=actor==null||actor.isBlank()?SecurityUtils.getUsername():actor;
  try{jdbc.update(connection->{var statement=connection.prepareStatement("INSERT INTO metal_process_type(factory_id,type_name,create_by,update_by) VALUES(?,?,?,?)",Statement.RETURN_GENERATED_KEYS);statement.setLong(1,factory);statement.setString(2,normalized);statement.setString(3,by);statement.setString(4,by);return statement;},key);}
  catch(DuplicateKeyException e){throw new ServiceException("工序类型已存在，请勿重复添加",409);}
  return read(Objects.requireNonNull(key.getKey()).longValue(),factory);
 }
 @Transactional(rollbackFor=Exception.class)
 public Map<String,Object> edit(long id,String name,Integer expectedVersion,Long requested){
  Long factory=scope.resolveFactory(requested);scope.lockFactories(List.of(factory));
  String normalized=validName(name);var row=lock(id,factory);version(row,expectedVersion);
  if(builtin(row))throw new ServiceException("系统内置工序类型不能改名",400);
  if(!normalized.equals(Objects.toString(row.get("name"),"")))requireUnused(id,Objects.toString(row.get("name"),""));
  try{if(jdbc.update("UPDATE metal_process_type SET type_name=?,version=version+1,update_by=?,update_time=NOW() WHERE type_id=? AND factory_id=? AND version=?",normalized,SecurityUtils.getUsername(),id,factory,expectedVersion)!=1)throw new ServiceException("工序类型资料已变化，请重新加载",409);}
  catch(DuplicateKeyException e){throw new ServiceException("工序类型已存在，请勿重复添加",409);}
  return read(id,factory);
 }
 @Transactional(rollbackFor=Exception.class)
 public void remove(Long id,Integer expectedVersion,Long requested){
  Long factory=scope.resolveFactory(requested);scope.lockFactories(List.of(factory));
  var row=lock(id,factory);version(row,expectedVersion);
  if(builtin(row))throw new ServiceException("系统内置工序类型不能删除",400);
  requireUnused(id,Objects.toString(row.get("name"),""));
  try{if(jdbc.update("DELETE FROM metal_process_type WHERE type_id=? AND factory_id=? AND version=?",id,factory,expectedVersion)!=1)throw new ServiceException("工序类型资料已变化，请重新加载",409);}
  catch(org.springframework.dao.DataIntegrityViolationException e){throw new ServiceException("工序类型已被引用，不能删除",409);}
 }
 /** @deprecated Prefer remove(id, version, factoryId). */
 @Transactional(rollbackFor=Exception.class)
 public void remove(Long id){
  Long factory=scope.catalogFactory("process-type",id);scope.lockFactories(List.of(factory));
  var row=lock(id,factory);
  if(builtin(row))throw new ServiceException("系统内置工序类型不能删除",400);
  if(jdbc.queryForObject("SELECT COUNT(*) FROM metal_process WHERE process_type=?",Integer.class,id)>0)throw new ServiceException("工序类型已被工序引用，不能删除");
  try{if(jdbc.update("DELETE FROM metal_process_type WHERE type_id=? AND factory_id=?",id,factory)!=1)throw new ServiceException("工序类型不存在或已删除");}
  catch(org.springframework.dao.DataIntegrityViolationException e){throw new ServiceException("工序类型已被引用，不能删除");}
 }
}
