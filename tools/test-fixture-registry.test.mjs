import {test,mock} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {trackFixture} from './test-fixture-registry.mjs';
test('fixture registry retries a transient Windows file lock without losing IDs',()=>{
 const prefix='工单验收_'+Date.now()+'_abcdef',file=new URL('../.runtime/test-fixtures/'+encodeURIComponent(prefix)+'.json',import.meta.url);
 const rename=fs.renameSync;let failures=0;
 const stub=mock.method(fs,'renameSync',(...args)=>{if(failures++<2)throw Object.assign(new Error('locked'),{code:'EPERM'});return rename(...args)});
 try{const made=trackFixture(prefix,{factories:[]});made.factories.push('123');assert.deepEqual(JSON.parse(fs.readFileSync(file,'utf8')).made.factories,['123']);assert.ok(failures>=3)}
 finally{stub.mock.restore();if(fs.existsSync(file))fs.unlinkSync(file)}
});
