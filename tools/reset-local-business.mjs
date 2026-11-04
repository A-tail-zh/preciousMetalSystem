import fs from 'node:fs';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {createHash,randomUUID} from 'node:crypto';
import assert from 'node:assert/strict';
import net from 'node:net';
import {env} from './local-test-client.mjs';
const sql=query=>execFileSync('D:/mysql8.4/mysql-8.4.11-winx64/bin/mysql.exe',['--no-defaults','--no-login-paths','--protocol=TCP','--default-character-set=utf8mb4','-h','127.0.0.1','-P',env.MYSQL_PORT,'-u',env.MYSQL_USER,'-N','-B','--raw','heavy_metal','-e',query],{encoding:'utf8',env:{...process.env,MYSQL_PWD:env.MYSQL_PASSWORD},maxBuffer:128*1024*1024}).trim();
const cleared=new Set(('metal_type_quote_rule metal_batch_return_confirmation metal_batch_return_document metal_customer metal_factory metal_inbound_submission metal_inventory metal_line_product metal_material metal_outbound_submission metal_process metal_process_batch metal_process_raw_return_line metal_product metal_product_process metal_production_line metal_report_material metal_route_skip_permit metal_route_skip_permit_version metal_route_skip_use metal_saved_quotation metal_quotation_request metal_quotation_image metal_semi_auto_release_event metal_semi_auto_release_result metal_semi_flow_event metal_semi_release metal_semi_release_use metal_stock_ledger metal_warehouse metal_warehouse_number metal_warehouse_order metal_warehouse_order_line metal_warehouse_request metal_warning_history metal_warning_record metal_warning_rule metal_weight_stage_event metal_work_order metal_work_order_close metal_work_order_item metal_work_order_legacy_plan metal_work_order_number metal_work_order_review metal_work_order_step metal_work_report').split(' '));
const preservedMetal=new Set(['metal_quote_rule','metal_volume_rule','metal_stock_sequence','metal_work_order_sequence']);
const partial=new Map([['metal_type','metal_type_id<>1'],['metal_process_type','type_id NOT BETWEEN 1 AND 9']]);
export function classify(table){if(cleared.has(table))return 'CLEAR';if(preservedMetal.has(table))return 'PRESERVE';if(partial.has(table))return 'PRESERVE_BUILTINS';if(table==='flyway_schema_history'||/^(sys_|audit_|qrtz_|gen_)/.test(table))return 'PRESERVE';throw Error('Unclassified table: '+table)}
export function deletionOrder(tables,foreignKeys){const pending=new Set(tables),order=[];while(pending.size){const leaves=[...pending].filter(t=>!foreignKeys.some(([child,parent])=>parent===t&&child!==t&&pending.has(child)));if(!leaves.length)throw Error('Foreign key cycle');for(const table of leaves){pending.delete(table);order.push(table)}}return order}
if(process.argv.includes('--self-test')){assert.equal(classify('metal_customer'),'CLEAR');assert.equal(classify('sys_user'),'PRESERVE');assert.throws(()=>classify('metal_unreviewed'));assert.deepEqual(deletionOrder(['parent','child'],[['child','parent']]),['child','parent']);assert.throws(()=>deletionOrder(['a','b'],[['a','b'],['b','a']]));console.log('5 reset safety self-checks passed');process.exit(0)}
const rehearsal=process.argv.includes('--rehearsal'),execute=process.argv.includes('--execute');
const expectedPort=rehearsal?'3317':'3308';assert.equal(sql('SELECT DATABASE(),@@port'),'heavy_metal	'+expectedPort,'Unexpected target database');
assert.equal(String(env.MYSQL_PORT),expectedPort);
const base=path.resolve('.runtime/work-order-v2-20261102'),marker=path.join(base,rehearsal?'reset-rehearsal-complete.json':'reset-main-complete.json');
const startedMarker=marker.replace('-complete.json','-started.json');
if(execute&&(fs.existsSync(marker)||fs.existsSync(startedMarker)))throw Error('Reset already completed or needs recovery; refusing to remove regenerated data');
if(execute&&!rehearsal){const listening=await new Promise(resolve=>{const socket=net.connect({host:'127.0.0.1',port:8080});socket.once('connect',()=>{socket.destroy();resolve(true)});socket.once('error',()=>resolve(false));socket.setTimeout(2000,()=>{socket.destroy();resolve(true)})});assert.equal(listening,false,'Stop this project backend before reset');assert.ok(fs.existsSync(path.join(base,'reset-rehearsal-complete.json')),'Restore and reset rehearsal required')}
const tables=sql("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME").split(/\r?\n/);
for(const table of tables){assert.match(table,/^[a-z0-9_]+$/);classify(table)}
const targets=tables.filter(t=>cleared.has(t));
const fk=sql("SELECT TABLE_NAME,REFERENCED_TABLE_NAME,COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL").split(/\r?\n/).map(s=>s.split('\t'));
for(const [child,parent,column] of fk)if(targets.includes(parent)&&!targets.includes(child)&&!(['sys_user','sys_role'].includes(child)&&parent==='metal_factory'&&column==='factory_id'))throw Error('Preserved reference: '+child+' -> '+parent);
for(const [child,parent,column]of fk){
 if(child===parent&&targets.includes(child)&&!((child==='metal_stock_ledger'&&column==='reversal_of')||(child==='metal_warehouse_order_line'&&column==='source_line_id')))throw Error('Unreviewed self reference: '+child+'.'+column);
}
const engines=Object.fromEntries(sql("SELECT TABLE_NAME,ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE'").split(/\r?\n/).map(row=>row.split('\t')));
for(const table of [...targets,...partial.keys(),'sys_user','sys_role','sys_oper_log'])assert.equal(engines[table],'InnoDB','Reset requires transactional table '+table);
const order=deletionOrder(targets,fk);
const columns=table=>sql("SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='"+table+"' ORDER BY ORDINAL_POSITION").split(/\r?\n/);
function digest(table,where='1=1',omit=[]){const cols=columns(table).filter(c=>!omit.includes(c));for(const c of cols)assert.match(c,/^[a-zA-Z0-9_]+$/);const rows=sql('SELECT SHA2(CAST(JSON_ARRAY('+cols.map(c=>'HEX(CAST('+String.fromCharCode(96)+c+String.fromCharCode(96)+' AS BINARY))').join(',')+') AS CHAR),256) FROM '+table+' WHERE '+where).split(/\r?\n/).filter(Boolean).sort();return createHash('sha256').update(rows.join('\n')).digest('hex')}
const autoIncrements=()=>Object.fromEntries(sql("SELECT TABLE_NAME,COALESCE(AUTO_INCREMENT,0) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE'").split(/\r?\n/).map(row=>{const [table,value]=row.split('\t');return[table,Number(value)]}));
const beforeAutoIncrement=autoIncrements();
const before=Object.fromEntries(tables.map(t=>[t,Number(sql('SELECT COUNT(*) FROM '+t))]));
const keep=tables.filter(t=>!cleared.has(t));const protectedWhere={metal_type:'metal_type_id=1',metal_process_type:'type_id BETWEEN 1 AND 9'};
const maxOper=sql('SELECT COALESCE(MAX(oper_id),0) FROM sys_oper_log');protectedWhere.sys_oper_log='oper_id<='+maxOper;
const omits={sys_user:['factory_id','version','auth_version'],sys_role:['factory_id','version']};
const protectedCounts=Object.fromEntries(keep.map(t=>[t,Number(sql('SELECT COUNT(*) FROM '+t+' WHERE '+(protectedWhere[t]||'1=1')))]));
const digests=Object.fromEntries(keep.map(t=>[t,digest(t,protectedWhere[t]||'1=1',omits[t]||[])]));
const stamp=new Date().toISOString().replace(/[:.]/g,'-'),dir=path.join(base,(rehearsal?'rehearsal':'main-reset')+'-'+stamp);fs.mkdirSync(dir,{recursive:true});
const backup=path.join(dir,'before.sql');
execFileSync('D:/mysql8.4/mysql-8.4.11-winx64/bin/mysqldump.exe',['--no-defaults','--no-login-paths','--protocol=TCP','-h','127.0.0.1','-P',env.MYSQL_PORT,'-u',env.MYSQL_USER,'--single-transaction','--no-tablespaces','--set-gtid-purged=OFF','--hex-blob','--result-file='+backup,'heavy_metal'],{env:{...process.env,MYSQL_PWD:env.MYSQL_PASSWORD},stdio:['ignore','pipe','pipe']});
assert.ok(fs.statSync(backup).size>1000);assert.ok(fs.readFileSync(backup,'utf8').includes('Dump completed'));
const manifest={database:'heavy_metal',port:expectedPort,execute,rehearsal,before,beforeAutoIncrement,classification:Object.fromEntries(tables.map(t=>[t,classify(t)])),order,partial:Object.fromEntries(partial),digests,backup,sha256:createHash('sha256').update(fs.readFileSync(backup)).digest('hex')};fs.writeFileSync(path.join(dir,'manifest.json'),JSON.stringify(manifest,null,2));
if(!execute){console.log(JSON.stringify({dryRun:true,manifest:path.join(dir,'manifest.json'),targetRows:targets.reduce((sum,t)=>sum+before[t],0)}));process.exit(0)}
const event=randomUUID(),summary=JSON.stringify({event,action:'LOCAL_BUSINESS_RESET',manifest:path.relative(process.cwd(),path.join(dir,'manifest.json')),cleared:targets,before});
const quote=s=>"'"+s.replaceAll('\\','\\\\').replaceAll("'","''")+"'";
const statements=['START TRANSACTION','CREATE TEMPORARY TABLE reset_assert (ok INT CHECK(ok=1))',"UPDATE sys_user SET factory_id=NULL,version=version+1,auth_version=auth_version+1","UPDATE sys_role SET factory_id=NULL,version=version+1",'UPDATE metal_stock_ledger SET reversal_of=NULL WHERE reversal_of IS NOT NULL','UPDATE metal_warehouse_order_line SET source_line_id=NULL WHERE source_line_id IS NOT NULL',...order.map(t=>'DELETE FROM '+t),...Array.from(partial,([t,where])=>'DELETE FROM '+t+' WHERE '+where),...targets.map(t=>'INSERT INTO reset_assert SELECT IF(COUNT(*)=0,1,0) FROM '+t),'INSERT INTO reset_assert SELECT IF(COUNT(*)=0,1,0) FROM sys_user WHERE factory_id IS NOT NULL','INSERT INTO reset_assert SELECT IF(COUNT(*)=0,1,0) FROM sys_role WHERE factory_id IS NOT NULL',...keep.map(t=>'INSERT INTO reset_assert SELECT IF(COUNT(*)='+protectedCounts[t]+',1,0) FROM '+t+' WHERE '+(protectedWhere[t]||'1=1')),"INSERT INTO sys_oper_log(title,business_type,operator_type,oper_name,status,oper_time,module_code,action_code,event_id,objects_json) VALUES('本地业务及基础资料清理',3,1,'admin',0,NOW(),'local-reset','RESET',"+quote(event)+','+quote(summary)+')','COMMIT'];
fs.writeFileSync(startedMarker,JSON.stringify({directory:dir,event,startedAt:new Date().toISOString()}),{flag:'wx'});
fs.writeFileSync(path.join(dir,'execute.sql'),statements.join(';\n')+';\n');sql(statements.join(';'));
const after=Object.fromEntries(tables.map(t=>[t,Number(sql('SELECT COUNT(*) FROM '+t))]));
for(const t of targets)assert.equal(after[t],0,t);for(const t of keep)assert.equal(digest(t,protectedWhere[t]||'1=1',omits[t]||[]),digests[t],'Protected data changed: '+t);
const afterAutoIncrement=autoIncrements();for(const [table,value]of Object.entries(beforeAutoIncrement))assert.ok(afterAutoIncrement[table]>=value,'Auto increment decreased: '+table);
const result={...manifest,after,afterAutoIncrement,protectedDigestsUnchanged:true,auditEvent:event,completedAt:new Date().toISOString()};fs.writeFileSync(path.join(dir,'verification.json'),JSON.stringify(result,null,2));fs.writeFileSync(marker,JSON.stringify({directory:dir,backup,completedAt:result.completedAt}));console.log(JSON.stringify({complete:true,rehearsal,backup,deletedRows:targets.reduce((sum,t)=>sum+before[t],0),protectedDigestsUnchanged:true}));
