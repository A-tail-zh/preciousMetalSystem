import {assertEnvironment,assertFixtureOwnership,fixtureEnvironment,discoverFixtureResources} from './test-fixture-scope.mjs';
import fs from 'node:fs';import{fileURLToPath}from'node:url';import{randomUUID}from'node:crypto';
const directory=new URL('../.runtime/test-fixtures/',import.meta.url);fs.mkdirSync(directory,{recursive:true});
const valid=p=>/^工单验收_\d+_[a-f0-9]{6}$/.test(p);
export function trackFixture(prefix,made){
 if(!valid(prefix))throw Error('invalid fixture prefix');
 const file=new URL(encodeURIComponent(prefix)+'.json',directory),runId=process.env.TEST_RUN_ID||'manual-'+process.pid;
 const save=()=>{const value={prefix,runId,made,environment:fixtureEnvironment(),cleaned:false};const tmp=fileURLToPath(file)+'.'+randomUUID()+'.tmp';fs.writeFileSync(tmp,JSON.stringify(value));fs.renameSync(tmp,file)};
 for(const key of Object.keys(made))made[key]=new Proxy(made[key],{set(target,key,value){Reflect.set(target,key,value);save();return true}});
 save();return made;
}
export function fixtureCleaned(prefix){if(!valid(prefix))throw Error('invalid prefix');const file=new URL(encodeURIComponent(prefix)+'.json',directory);if(fs.existsSync(file)){const x=JSON.parse(fs.readFileSync(file));x.cleaned=true;fs.writeFileSync(file,JSON.stringify(x));}}
export async function cleanupRegisteredFixtures(runId){
 if(!runId)throw Error('run ID required');const {login,api,sql}=await import('./local-test-client.mjs');const {cleanupWorkOrderFixture}=await import('./work-order-test-fixture.mjs');const {cleanupWarehouseFixtures}=await import('./warehouse-test-fixture.mjs');
 for(const file of fs.readdirSync(directory).filter(x=>x.endsWith('.json'))){const x=JSON.parse(fs.readFileSync(new URL(encodeURIComponent(file),directory)));if(x.runId!==runId||x.cleaned)continue;if(!valid(x.prefix)||Object.values(x.made).flat().some(v=>!/^\d+$/.test(v)))throw Error('invalid registry scope');assertEnvironment(x.environment,sql);assertFixtureOwnership(x.prefix,x.made,sql);discoverFixtureResources(x.prefix,x.made,sql);assertFixtureOwnership(x.prefix,x.made,sql);const token=await login();
  try{assertEnvironment(x.environment,sql);assertFixtureOwnership(x.prefix,x.made,sql);const ids=x.made.factories;if(ids.length)sql('DELETE FROM metal_saved_quotation WHERE factory_id IN ('+ids.join(',')+')');for(let i=0;i<ids.length;i+=2)cleanupWarehouseFixtures({prefix:x.prefix,factoryA:ids[i],factoryB:ids[i+1]||ids[i],environment:x.environment});await cleanupWorkOrderFixture(x,token);console.error('cleaned interrupted fixture '+x.prefix)}finally{await api('/logout',token,'POST')}
 }
}
