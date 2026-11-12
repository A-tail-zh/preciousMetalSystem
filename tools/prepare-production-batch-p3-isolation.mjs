import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';

// This helper only creates an empty, uniquely named schema on the disposable instance.
const credentialsPath=process.env.P3_ISOLATION_CREDENTIALS_FILE||'.runtime/isolated-regression-20261105/environment.private.json';
const credentials=JSON.parse(fs.readFileSync(credentialsPath,'utf8'));
assert.equal(String(credentials.MYSQL_PORT),'3323','Refuse credentials for a non-isolated port');
assert(credentials.MYSQL_USER&&credentials.MYSQL_PASSWORD,'Explicit disposable credentials are required');
const mysql=process.env.P3_MYSQL_BIN||'D:/mysql8.4/mysql-8.4.11-winx64/bin/mysql.exe';
const args=['--no-defaults','--no-login-paths','--protocol=TCP','--default-character-set=utf8mb4','-h','127.0.0.1','-P','3323','-u',credentials.MYSQL_USER,'-N','-B','--raw'];
const env={...process.env,MYSQL_PWD:credentials.MYSQL_PASSWORD};
function query(sql){return execFileSync(mysql,[...args,'mysql'],{env,input:sql,encoding:'utf8'}).trim();}
assert.equal(query('SELECT @@port'),'3323');
const schema='production_batch_p3_20261112_'+crypto.randomBytes(5).toString('hex');
assert.match(schema,/^production_batch_p3_20261112_[a-f0-9]{10}$/);
assert.equal(query(`SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='${schema}'`),'0');
query(`CREATE DATABASE ${schema} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`);
const dir=path.resolve('.runtime',schema);
fs.mkdirSync(dir,{recursive:false});
const jdbcUrl=`jdbc:mysql://127.0.0.1:3323/${schema}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai`;
fs.writeFileSync(path.join(dir,'test-env.private.json'),JSON.stringify({PRODUCTION_BATCH_P3_TEST_JDBC_URL:jdbcUrl,PRODUCTION_BATCH_P3_TEST_USER:credentials.MYSQL_USER,PRODUCTION_BATCH_P3_TEST_PASSWORD:credentials.MYSQL_PASSWORD},null,2),{flag:'wx'});
const proof={host:'127.0.0.1',port:3323,schema,createdAt:new Date().toISOString(),credentialsFile:path.join(dir,'test-env.private.json'),sourceBusinessDataCopied:false};
fs.writeFileSync(path.join(dir,'isolation-proof.json'),JSON.stringify(proof,null,2),{flag:'wx'});
console.log(JSON.stringify({host:proof.host,port:proof.port,schema,proofFile:path.join(dir,'isolation-proof.json'),privateEnvironmentFile:proof.credentialsFile}));

