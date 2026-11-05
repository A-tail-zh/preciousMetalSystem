import {test} from 'node:test';
import assert from 'node:assert/strict';
import {assertRegisteredIsolation,guardIsolatedFetch} from './isolated-test-environment.mjs';

test('real writes reject main services and incomplete isolation configuration',()=>{
 const environment={TEST_MYSQL_PORT:'3323',TEST_DB_SCHEMA:'heavy_metal',TEST_API_BASE_URL:'http://127.0.0.1:8096',TEST_REDIS_PORT:'6396',TEST_FRONTEND_BASE_URL:'http://127.0.0.1:5241',TEST_MYSQL_PASSWORD:'fictional-test-password',TEST_ADMIN_LOGIN_PASSWORD:'fictional-test-admin'};
 const previous=Object.fromEntries(Object.keys(environment).map(k=>[k,process.env[k]]));
 try{
  Object.assign(process.env,environment);assert.equal(assertRegisteredIsolation().size,2);
  for(const [key,value] of [['TEST_MYSQL_PORT','3308'],['TEST_DB_SCHEMA','other'],['TEST_API_BASE_URL','http://127.0.0.1:8080'],['TEST_REDIS_PORT','6380'],['TEST_FRONTEND_BASE_URL','http://127.0.0.1:5220'],['TEST_ADMIN_LOGIN_PASSWORD','']]){
   process.env[key]=value;assert.throws(assertRegisteredIsolation);process.env[key]=environment[key];
  }
 }finally{for(const [key,value] of Object.entries(previous))if(value===undefined)delete process.env[key];else process.env[key]=value}
});
test('raw fetch cannot bypass the isolated client and request credentials remain unchanged',async()=>{
 const calls=[],original=async(...args)=>{calls.push(args);return 'response'},fetch=guardIsolatedFetch(original,new Set(['http://127.0.0.1:8096']));
 await assert.rejects(fetch('http://127.0.0.1:8080/metal/warehouse-order/template'),/refused/);assert.equal(calls.length,0);
 const request=new Request('http://127.0.0.1:8096/login',{method:'POST',body:'fictional'});
 assert.equal(await fetch(request),'response');assert.equal(calls[0][0],request);
});
