import fs from 'node:fs';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
assert.equal(process.env.TEST_MYSQL_PORT,'3321');assert.equal(process.env.TEST_DB_SCHEMA,'heavy_metal_flow');assert.equal(process.env.TEST_API_BASE_URL,'http://127.0.0.1:8091');
const scripts=['verify-warehouse-flow-live','verify-warehouse-reservations-live','verify-warehouse-residuals-live','verify-warehouse-recovery-ownership-live','verify-warehouse-custody-live','verify-warehouse-actor-scope-live','verify-warehouse-multi-processor-live','verify-warehouse-semi-compatibility-live','verify-acid-wash-warehouse-flow-live','verify-warehouse-customers-live'];
const results=[];
for(const script of scripts){
 const result=spawnSync(process.execPath,['tools/'+script+'.mjs'],{encoding:'utf8',env:process.env,maxBuffer:8*1024*1024});
 const output=(result.stdout||'')+(result.stderr||'');fs.writeFileSync('.runtime/warehouse-flow-20261103/'+script+'-final.log',output);const passed=(result.stdout?.match(/^PASS /gm)||[]).length;results.push({script,passed,exitCode:result.status});console.log(script+': '+passed+' checks; exit '+result.status);
 fs.writeFileSync('.runtime/warehouse-flow-20261103/live-final-suite.json',JSON.stringify({time:new Date().toISOString(),results,passed:results.reduce((sum,r)=>sum+r.passed,0),complete:results.length===scripts.length&&results.every(r=>r.exitCode===0)},null,2));
 if(result.status!==0){console.error(output.slice(-5000));process.exit(result.status||1)}
}
