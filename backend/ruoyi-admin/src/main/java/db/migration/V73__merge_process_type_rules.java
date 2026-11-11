package db.migration;

import com.ruoyi.metal.service.ProcessTypeMigration;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

public class V73__merge_process_type_rules extends BaseJavaMigration {
 @Override public boolean canExecuteInTransaction(){return false;}
 @Override public Integer getChecksum(){return 20261110;}
 @Override public void migrate(Context context)throws Exception{
  var connection=context.getConnection();boolean autoCommit=connection.getAutoCommit();
  connection.setAutoCommit(false);
  try{
   ProcessTypeMigration.migrate(new JdbcTemplate(new SingleConnectionDataSource(connection,true)));
   connection.commit();
  }catch(Exception e){connection.rollback();throw e;}
  finally{connection.setAutoCommit(autoCommit);}
 }
}
