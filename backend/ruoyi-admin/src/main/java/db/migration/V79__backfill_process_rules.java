package db.migration;

import com.ruoyi.metal.service.ProcessRuleMigration;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

public class V79__backfill_process_rules extends BaseJavaMigration {
    @Override public boolean canExecuteInTransaction() { return false; }
    @Override public Integer getChecksum() { return 20261111; }
    @Override public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            ProcessRuleMigration.migrate(new JdbcTemplate(new SingleConnectionDataSource(connection, true)));
            connection.commit();
        } catch (Exception e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }
}
