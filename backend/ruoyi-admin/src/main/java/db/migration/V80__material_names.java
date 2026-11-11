package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.sql.SQLException;
import java.util.ArrayList;

/** Fill catalog names without changing historical inventory or document snapshots. */
public class V80__material_names extends BaseJavaMigration {
 @Override public Integer getChecksum(){return 20261111;}
 @Override public void migrate(Context context)throws Exception{
  var connection=context.getConnection();
  try(var statement=connection.createStatement()){
   var invalid=new ArrayList<Long>();
   try(var rows=statement.executeQuery("SELECT material_id FROM metal_material WHERE NULLIF(TRIM(material_name),'') IS NULL AND NULLIF(TRIM(material_type),'') IS NULL ORDER BY material_id")){
    while(rows.next())invalid.add(rows.getLong(1));
   }
   if(!invalid.isEmpty())throw new SQLException("物料名称无法补齐，请先核对物料ID："+invalid);
   statement.executeUpdate("UPDATE metal_material SET material_name=TRIM(material_type) WHERE NULLIF(TRIM(material_name),'') IS NULL");
  }
 }
}
