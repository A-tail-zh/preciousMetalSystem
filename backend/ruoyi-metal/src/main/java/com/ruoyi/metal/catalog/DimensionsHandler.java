package com.ruoyi.metal.catalog;
import java.sql.*;
import java.util.Map;
import org.apache.ibatis.type.*;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
/**
 * 将产品尺寸映射与数据库中的 JSON 字段相互转换。
 */
public class DimensionsHandler extends BaseTypeHandler<Map<String,String>> {
    @Override public void setNonNullParameter(PreparedStatement s,int i,Map<String,String> v,JdbcType t)throws SQLException{s.setString(i,JSON.toJSONString(v));}
    private Map<String,String> parse(String s){return s==null?Map.of():JSON.parseObject(s,new TypeReference<Map<String,String>>(){});}
    @Override public Map<String,String> getNullableResult(ResultSet r,String c)throws SQLException{return parse(r.getString(c));}
    @Override public Map<String,String> getNullableResult(ResultSet r,int c)throws SQLException{return parse(r.getString(c));}
    @Override public Map<String,String> getNullableResult(CallableStatement s,int c)throws SQLException{return parse(s.getString(c));}
}
