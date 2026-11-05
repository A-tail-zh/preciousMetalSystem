import assert from 'node:assert/strict';

export function assertRegisteredIsolation(){
 const bulk=process.env.TEST_DB_SCHEMA==='audit_bulk_20261023';
 assert.equal(process.env.TEST_MYSQL_PORT,'3323','Only the registered disposable MySQL instance is allowed');
 assert.equal(process.env.TEST_DB_SCHEMA,bulk?'audit_bulk_20261023':'heavy_metal');
 assert.equal(process.env.TEST_API_BASE_URL,bulk?'http://127.0.0.1:18096':'http://127.0.0.1:8096');
 assert.equal(process.env.TEST_REDIS_PORT,bulk?'6398':'6396');
 assert.equal(process.env.TEST_FRONTEND_BASE_URL,bulk?'http://127.0.0.1:15441':'http://127.0.0.1:5241');
 assert(process.env.TEST_MYSQL_PASSWORD&&process.env.TEST_ADMIN_LOGIN_PASSWORD,'Explicit disposable credentials are required');
 return new Set([process.env.TEST_API_BASE_URL,process.env.TEST_FRONTEND_BASE_URL]);
}

export function guardIsolatedFetch(fetch,origins){
 return (input,init)=>{
  const url=new URL(typeof input==='string'||input instanceof URL?input:input.url);
  if(['http:','https:'].includes(url.protocol)&&!origins.has(url.origin))return Promise.reject(new Error('Isolated client refused network target: '+url.origin+url.pathname));
  return fetch(input,init);
 };
}
