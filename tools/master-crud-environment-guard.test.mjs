import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
const source=readFileSync(new URL('../frontend/tests/master-crud.spec.ts',import.meta.url),'utf8');
const guard=source.match(/function assertIsolatedCrudEnvironment\(\)\{[\s\S]*?\n\}/)[0];
test('every real CRUD test rejects the default main environment before login or fixture creation',()=>{
 assert(source.indexOf('  assertIsolatedCrudEnvironment()')<source.indexOf('const productFixture='));
 for(const environment of [{port:'3308',schema:'heavy_metal',api:'http://127.0.0.1:8080'},{port:'3321',schema:'heavy_metal',api:'http://127.0.0.1:8091'},{port:'3321',schema:'heavy_metal_factory_20261104',api:'http://127.0.0.1:8080'}])assert.throws(()=>vm.runInNewContext(guard+';assertIsolatedCrudEnvironment()',{fixtureEnvironment:()=>environment}),/登记隔离环境/);
 assert.doesNotThrow(()=>vm.runInNewContext(guard+';assertIsolatedCrudEnvironment()',{fixtureEnvironment:()=>({port:'3321',schema:'heavy_metal_factory_20261104',api:'http://127.0.0.1:8091'})}));
});
