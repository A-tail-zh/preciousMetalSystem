import fs from 'node:fs';import path from 'node:path';import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';import {createHash} from 'node:crypto';
import {env} from './local-test-client.mjs';
const dir=path.resolve('.runtime/processing-v2-20261103'),bin='D:/mysql8.4/mysql-8.4.11-winx64/bin/',backup=path.join(dir,'main-before-v53.sql');
assert.equal(process.env.TEST_MYSQL_PORT,undefined);assert.equal(env.MYSQL_PORT,'3308');fs.mkdirSync(dir,{recursive:true});
const hash=value=>createHash('sha256').update(value).digest('hex');
const identifier=value=>{assert(/^[A-Za-z0-9_]+$/.test(value));return '`'+value+'`';};
function client(port,user,password,schema){const args=['--no-defaults','--no-login-paths','--default-character-set=utf8mb4','--protocol=TCP','-h','127.0.0.1','-P',String(port),'-u',user],options={encoding:'utf8',env:{...process.env,MYSQL_PWD:password},maxBuffer:256*1024*1024};const query=q=>execFileSync(bin+'mysql.exe',[...args,'-N','-B','--raw',schema,'-e',q],options).trim();assert.equal(query('SELECT @@port'),String(port));return {args,options,query,schema};}
const main=()=>client(3308,env.MYSQL_USER,env.MYSQL_PASSWORD,'heavy_metal');
const auditTables=new Set(['sys_oper_log','sys_logininfor','sys_runtime_event','sys_audit_outbox','metal_inventory_origin_verification','metal_raw_plan_change','metal_raw_qualification_use','metal_semi_qualification_use']);
function snapshot(c,definition){
 const tables=definition?Object.keys(definition.tables):c.query("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' ORDER BY table_name").split(/\r?\n/);
 const result={schema:c.schema,port:Number(c.query('SELECT @@port')),tables:{},flyway:c.query('SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1'),time:new Date().toISOString()};
 for(const table of tables){
  const old=definition?.tables[table],columns=old?.columns??c.query(`SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='${table}' ORDER BY ordinal_position`).split(/\r?\n/).filter(n=>table!=='sys_user'||!['login_date','login_ip'].includes(n));
  const keys=old?.keys??c.query(`SELECT column_name FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name='${table}' AND constraint_name='PRIMARY' ORDER BY ordinal_position`).split(/\r?\n/).filter(Boolean);assert(keys.length,'table needs primary key: '+table);
  const appendOnly=auditTables.has(table)||table==='flyway_schema_history';
  const last=old?.last??(appendOnly&&keys.length===1?c.query('SELECT COALESCE(MAX('+identifier(keys[0])+'),0) FROM '+identifier(table)):null);
  let where=table==='sys_menu'?" WHERE perms IS NULL OR perms<>'metal:inventory:verify-origin'":'';
  if(appendOnly&&last!==null){assert(/^\d+$/.test(last));where+=(where?' AND ':' WHERE ')+identifier(keys[0])+'<='+last;}
  const selected=columns.map(identifier).join(','),order=keys.map(identifier).join(',');
  result.tables[table]={columns,keys,last,appendOnly,count:Number(c.query('SELECT COUNT(*) FROM '+identifier(table)+where)),hash:hash(c.query('SELECT '+selected+' FROM '+identifier(table)+where+' ORDER BY '+order))};
 }
 return result;
}
const movingAuditTables=new Set(['audit_file_cursor','audit_outbox','audit_scope','sys_runtime_log','sys_job_log','audit_cleanup_history']);
function compare(before,after,allowAuditMovement=false){for(const [table,old]of Object.entries(before.tables)){if(allowAuditMovement&&movingAuditTables.has(table))continue;assert.equal(after.tables[table].count,old.count,'row count changed: '+table);assert.equal(after.tables[table].hash,old.hash,'existing data changed: '+table);}}
function verifyAuditHistory(current,before){
 const password=JSON.parse(fs.readFileSync(path.resolve('.runtime/warehouse-flow-20261103/root-private.json'))).password,restored=client(3321,'root',password,before.restoreSchema||'heavy_metal_v53_restore');
 const literal=value=>"'"+value.replaceAll('\\','\\\\').replaceAll("'","''")+"'";
 for(const table of movingAuditTables){
  const saved=before.tables[table];if(!saved)continue;const keys=saved.keys,columns=saved.columns.filter(c=>table!=='audit_outbox'||!['status','attempts','next_retry','delivered_time'].includes(c));
  const keyRows=restored.query('SELECT '+keys.map(identifier).join(',')+' FROM '+identifier(table)+' ORDER BY '+keys.map(identifier).join(','));if(!keyRows)continue;
  const tuples=keyRows.split(/\r?\n/).map(row=>row.split('\t'));const where=' WHERE '+(keys.length===1?identifier(keys[0]):'('+keys.map(identifier).join(',')+')')+' IN ('+tuples.map(row=>keys.length===1?literal(row[0]):'('+row.map(literal).join(',')+')').join(',')+')';
  if(table==='audit_file_cursor'){
   for(const [name,offset]of restored.query('SELECT file_name,file_offset FROM audit_file_cursor').split(/\r?\n/).map(row=>row.split('\t')))assert(Number(current.query('SELECT file_offset FROM audit_file_cursor WHERE file_name='+literal(name)))>=Number(offset),'audit file cursor rewound or disappeared');
  }else{
   const query='SELECT '+columns.map(identifier).join(',')+' FROM '+identifier(table)+where+' ORDER BY '+keys.map(identifier).join(',');assert.equal(hash(current.query(query)),hash(restored.query(query)),'existing audit history changed: '+table);
  }
 }
}
const action=process.argv[2];
if(action==='backup'||action==='release-backup'){
 const release=action==='release-backup',file=release?path.join(dir,'main-release-before-v53.sql'):backup;assert(!fs.existsSync(file),'never overwrite delivery backup');const c=main(),before=snapshot(c);assert.equal(before.flyway,'52');
 execFileSync(bin+'mysqldump.exe',[...c.args,'--single-transaction','--no-tablespaces','--set-gtid-purged=OFF','--routines','--triggers','--events','--hex-blob','--result-file='+file,c.schema],{...c.options,encoding:undefined});
 const after=snapshot(c,before);compare(before,after);const evidence={...before,restoreSchema:release?'heavy_metal_v53_release_restore':'heavy_metal_v53_restore',backupSha256:hash(fs.readFileSync(file)),backupBytes:fs.statSync(file).size};fs.writeFileSync(path.join(dir,release?'main-release-before-v53.json':'main-before-v53.json'),JSON.stringify(evidence,null,2));console.log(JSON.stringify({action,version:52,backupBytes:evidence.backupBytes,backupSha256:evidence.backupSha256,existingTables:Object.keys(before.tables).length}));
}else if(action==='restore-check'||action==='release-restore-check'){
 const release=action==='release-restore-check',file=release?path.join(dir,'main-release-before-v53.sql'):backup,before=JSON.parse(fs.readFileSync(path.join(dir,release?'main-release-before-v53.json':'main-before-v53.json')));assert.equal(hash(fs.readFileSync(file)),before.backupSha256);
 const password=JSON.parse(fs.readFileSync(path.resolve('.runtime/warehouse-flow-20261103/root-private.json'))).password,c=client(3321,'root',password,'mysql'),schema=release?'heavy_metal_v53_release_restore':'heavy_metal_v53_restore';assert.equal(c.query(`SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='${schema}'`),'0','restore database already exists; preserve it');
 c.query(`CREATE DATABASE ${schema} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci`);execFileSync(bin+'mysql.exe',[...c.args,schema],{...c.options,input:fs.readFileSync(file)});
 const restored=snapshot(client(3321,'root',password,schema),before);compare(before,restored);assert.equal(restored.flyway,'52');fs.writeFileSync(path.join(dir,release?'main-release-restore-check.json':'main-v53-restore-check.json'),JSON.stringify(restored,null,2));console.log('RESTORE VERIFIED: all original tables, row counts and data digests match V52 backup');
}else if(action==='reservation-preflight'){
 const before=JSON.parse(fs.readFileSync(path.join(dir,'main-before-v53.json'))),c=main(),after=snapshot(c,before),password=JSON.parse(fs.readFileSync(path.resolve('.runtime/warehouse-flow-20261103/root-private.json'))).password,restored=client(3321,'root',password,'heavy_metal_v53_restore');
 for(const table of Object.keys(before.tables))if(!movingAuditTables.has(table)&&!['metal_stock_sequence','metal_warehouse_number'].includes(table)){assert.equal(after.tables[table].count,before.tables[table].count,table);assert.equal(after.tables[table].hash,before.tables[table].hash,table);}
 verifyAuditHistory(c,before);const current=new Map(c.query('SELECT sequence_date,prefix,sequence_value FROM metal_stock_sequence').split(/\r?\n/).map(s=>{const r=s.split('\t');return [r[0]+':'+r[1],Number(r[2])];}));
 for(const row of restored.query('SELECT sequence_date,prefix,sequence_value FROM metal_stock_sequence').split(/\r?\n/).map(s=>s.split('\t')))assert(current.get(row[0]+':'+row[1])>=Number(row[2]),'number sequence must not decrease');
 const ids=restored.query('SELECT request_id FROM metal_warehouse_number ORDER BY request_id').split(/\r?\n/);assert(ids.every(id=>/^[a-fA-F0-9-]{36}$/.test(id)));const query="SELECT * FROM metal_warehouse_number WHERE request_id IN ("+ids.map(id=>"'"+id+"'").join(',')+") ORDER BY request_id";assert.equal(hash(c.query(query)),hash(restored.query(query)),'old number reservations changed');
 const evidence={time:new Date().toISOString(),beforeReservations:before.tables.metal_warehouse_number.count,currentReservations:after.tables.metal_warehouse_number.count,businessAndMasterUnchanged:true,oldReservationsUnchanged:true,sequencesMonotonic:true};fs.writeFileSync(path.join(dir,'number-reservation-delta.json'),JSON.stringify(evidence,null,2));console.log(JSON.stringify(evidence));
}else if(action==='drift'){
 const before=JSON.parse(fs.readFileSync(path.join(dir,'main-before-v53.json'))),after=snapshot(main(),before);console.log(JSON.stringify(Object.keys(before.tables).filter(t=>!movingAuditTables.has(t)&&(before.tables[t].count!==after.tables[t].count||before.tables[t].hash!==after.tables[t].hash)).map(table=>({table,beforeCount:before.tables[table].count,currentCount:after.tables[table].count})),null,2));
}else if(['preflight','verify-main'].includes(action)){
 const baseline=fs.existsSync(path.join(dir,'main-release-before-v53.json'))?'main-release-before-v53.json':'main-before-v53.json',before=JSON.parse(fs.readFileSync(path.join(dir,baseline))),c=main(),after=snapshot(c,before);compare(before,after,true);verifyAuditHistory(c,before);assert.equal(after.flyway,action==='preflight'?'52':'53');
 if(action==='verify-main'){assert.equal(c.query("SELECT COUNT(*) FROM sys_menu WHERE perms='metal:inventory:verify-origin'"),'1');assert.equal(c.query("SELECT COUNT(*) FROM sys_role_menu r JOIN sys_menu m ON m.menu_id=r.menu_id WHERE m.perms='metal:inventory:verify-origin' AND r.role_id<>1"),'0');}
 fs.writeFileSync(path.join(dir,action+'.json'),JSON.stringify(after,null,2));console.log(action.toUpperCase()+': original business/demo/master data and existing audit digests unchanged');
}else throw Error('expected backup, restore-check, preflight or verify-main');
