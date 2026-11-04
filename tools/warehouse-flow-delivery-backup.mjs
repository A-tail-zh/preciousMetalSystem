import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {env} from './local-test-client.mjs';

const dir=path.resolve('.runtime/warehouse-flow-20261103'),backup=path.join(dir,'main-before-v50-v52.sql'),bin='D:/mysql8.4/mysql-8.4.11-winx64/bin/';
assert(dir.startsWith(path.resolve('.runtime')+path.sep));assert.equal(process.env.TEST_MYSQL_PORT,undefined,'Do not use a test override for the main backup');assert.equal(env.MYSQL_PORT,'3308');
const newPermissions="'metal:warehouse-order:receive','metal:warehouse-order:handover','metal:warehouse-order:accept','metal:inventory:export'";
const hash=bytes=>createHash('sha256').update(bytes).digest('hex');
function client(port,user,password,schema){
 const args=['--no-defaults','--no-login-paths','--default-character-set=utf8mb4','--protocol=TCP','-h','127.0.0.1','-P',String(port),'-u',user],options={encoding:'utf8',env:{...process.env,MYSQL_PWD:password},maxBuffer:256*1024*1024};
 const query=sql=>execFileSync(bin+'mysql.exe',[...args,'-N','-B','--raw',schema,'-e',sql],options).trim();assert.equal(query('SELECT @@port'),String(port));
 return {query,args,options,schema};
}
const main=()=>client(3308,env.MYSQL_USER,env.MYSQL_PASSWORD,'heavy_metal');
const protectedTables=['metal_factory','metal_production_line','metal_line_product','metal_customer','metal_product','metal_product_process','metal_material','metal_process','metal_process_type','metal_type','metal_type_quote_rule','metal_volume_rule','metal_saved_quotation','metal_warning_rule','metal_route_skip_permit','metal_route_skip_permit_version','sys_user','sys_role','sys_user_role','sys_role_menu','sys_config','sys_dict_type','sys_dict_data'];
function snapshot(c){
 const tables=c.query("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' ORDER BY table_name").split(/\r?\n/);assert(tables.every(t=>/^[a-zA-Z0-9_]+$/.test(t)));
 const counts=Object.fromEntries(tables.map(t=>[t,Number(c.query('SELECT COUNT(*) FROM `'+t+'`'))]));const digests={};
 for(const table of [...protectedTables.filter(t=>tables.includes(t)),'sys_menu']){
  const keys=c.query("SELECT column_name FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name='"+table+"' AND constraint_name='PRIMARY' ORDER BY ordinal_position").split(/\r?\n/).filter(Boolean);assert(keys.length&&keys.every(k=>/^[a-zA-Z0-9_]+$/.test(k)));
  const rows=c.query('SELECT * FROM `'+table+'`'+(table==='sys_menu'?" WHERE perms IS NULL OR perms NOT IN ("+newPermissions+")":'')+' ORDER BY '+keys.map(k=>'`'+k+'`').join(','));digests[table]=hash(rows);
 }
 return {schema:c.schema,port:Number(c.query('SELECT @@port')),counts,protectedDigests:digests,flyway:c.query('SELECT COALESCE(MAX(CAST(version AS UNSIGNED)),0) FROM flyway_schema_history WHERE success=1'),time:new Date().toISOString()};
}
function compare(before,after){
 for(const [table,digest]of Object.entries(before.protectedDigests))assert.equal(after.protectedDigests[table],digest,'protected data changed: '+table);
 for(const [table,count]of Object.entries(before.counts))if(table.startsWith('metal_')&&!['metal_warehouse_number','metal_sequence'].includes(table))assert.equal(after.counts[table],count,'business data count changed: '+table);
}
const action=process.argv[2];
if(action==='backup'){
 assert(!fs.existsSync(backup),'Existing delivery backup must not be overwritten');const c=main(),before=snapshot(c);assert.equal(before.flyway,'49');
 execFileSync(bin+'mysqldump.exe',[...c.args,'--single-transaction','--no-tablespaces','--set-gtid-purged=OFF','--routines','--triggers','--events','--hex-blob','--result-file='+backup,c.schema],{...c.options,encoding:undefined});assert(fs.statSync(backup).size>0);const after=snapshot(c);compare(before,after);const evidence={...before,backupFile:backup,backupBytes:fs.statSync(backup).size,backupSha256:hash(fs.readFileSync(backup))};fs.writeFileSync(path.join(dir,'main-before-v50-v52.json'),JSON.stringify(evidence,null,2));console.log('MAIN BACKUP VERIFIED: V49; bytes='+evidence.backupBytes+'; sha256='+evidence.backupSha256);
}else if(action==='restore-check'){
 const saved=JSON.parse(fs.readFileSync(path.join(dir,'main-before-v50-v52.json')));assert.equal(hash(fs.readFileSync(backup)),saved.backupSha256);const password=JSON.parse(fs.readFileSync(path.join(dir,'root-private.json'))).password,root=client(3321,'root',password,'mysql'),schema='heavy_metal_delivery_rehearsal';assert.equal(root.query("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='"+schema+"'"),'0','rehearsal database already exists; preserve it');root.query('CREATE DATABASE '+schema+' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci');assert(/^[a-zA-Z0-9_]+$/.test(env.MYSQL_USER));root.query("GRANT ALL ON "+schema+".* TO '"+env.MYSQL_USER+"'@'127.0.0.1'");execFileSync(bin+'mysql.exe',[...root.args,schema],{...root.options,input:fs.readFileSync(backup,'utf8')});const restored=snapshot(client(3321,'root',password,schema));compare(saved,restored);assert.equal(restored.flyway,'49');fs.writeFileSync(path.join(dir,'main-restore-rehearsal.json'),JSON.stringify(restored,null,2));console.log('ISOLATED RESTORE VERIFIED: original table counts and protected data digests match; V49.');
}else if(action==='preflight'){
 const saved=JSON.parse(fs.readFileSync(path.join(dir,'main-before-v50-v52.json'))),current=snapshot(main());compare(saved,current);assert.equal(current.flyway,'49');console.log('MAIN PREFLIGHT: V49; backup still matches original business counts and protected data.');
}else if(action==='verify-rehearsal'||action==='verify-main'){
 const saved=JSON.parse(fs.readFileSync(path.join(dir,'main-before-v50-v52.json'))),c=action==='verify-main'?main():client(3321,env.MYSQL_USER,env.MYSQL_PASSWORD,'heavy_metal_delivery_rehearsal'),after=snapshot(c);compare(saved,after);assert.equal(after.flyway,'52');assert.equal(c.query('SELECT COUNT(*) FROM sys_menu WHERE perms IN ('+newPermissions+')'),'4');fs.writeFileSync(path.join(dir,action+'.json'),JSON.stringify(after,null,2));console.log(action.toUpperCase()+': V52; original business row counts and protected data digests unchanged; four new permissions present.');
}else throw Error('expected backup, restore-check, verify-rehearsal or verify-main');
