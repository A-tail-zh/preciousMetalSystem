package com.ruoyi.metal.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import static com.ruoyi.metal.service.StorageSupport.*;

/** Explicit disposable rehearsal CLI. Credentials are supplied only through its environment. */
public class ProductionBatchLegacyAdoption {
 public static void main(String[] args)throws Exception {
  if(args.length<5)throw new IllegalArgumentException("preview schema plan manifest actor | apply schema manifest request actor hash");String schema=args[1];
  if(!schema.matches("production_batch_p5_20261112_[a-f0-9]{10}")||schema.endsWith("762c61d418"))throw new IllegalArgumentException("Only an explicit new isolation schema is accepted");
  String url=System.getenv("PRODUCTION_BATCH_P5_TEST_JDBC_URL");if(url==null||!url.matches("jdbc:mysql://127\\.0\\.0\\.1:3323/"+schema+"(?:\\?.*)?"))throw new IllegalArgumentException("Explicit isolated URL required");
  var ds=new DriverManagerDataSource(url,System.getenv("PRODUCTION_BATCH_P5_TEST_USER"),System.getenv("PRODUCTION_BATCH_P5_TEST_PASSWORD"));var j=new JdbcTemplate(ds);if(j.queryForObject("SELECT @@port",Integer.class)!=3323||j.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='85' AND success=1",Integer.class)!=1)throw new IllegalStateException("Isolated V85 required");
  var service=new ProductionBatchLegacyAdoptionService(j);var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));tx.setIsolationLevel(java.sql.Connection.TRANSACTION_SERIALIZABLE);long actor=id(args[4]);
  if("preview".equals(args[0])){if(args.length!=5)throw new IllegalArgumentException("preview argument count");tx.setReadOnly(true);var plan=JSON.parseObject(Files.readString(Path.of(args[2])));var manifest=tx.execute(s->service.preview(actor,plan));Files.writeString(Path.of(args[3]),JSON.toJSONString(manifest,JSONWriter.Feature.WriteMapNullValue));System.out.println(JSON.toJSONString(Map.of("schema",schema,"manifestHash",hash(manifest),"orderId",manifest.get("orderId"),"batchCount",((List<?>)manifest.get("batches")).size())));}
  else if("apply".equals(args[0])){if(args.length!=6)throw new IllegalArgumentException("apply requires exact manifest hash");var manifest=JSON.parseObject(Files.readString(Path.of(args[2])));var result=tx.execute(s->service.adopt(actor,args[3],args[5],manifest));System.out.println(JSON.toJSONString(Map.of("schema",schema,"operationId",result.get("operationId"),"orderId",result.get("orderId"),"batches",result.get("batches"),"committed",true)));}
  else throw new IllegalArgumentException("Unknown command");
 }
}
