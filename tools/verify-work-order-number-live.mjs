import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {api,ok,login} from './local-test-client.mjs';
const token=await login();const reserve=async requestId=>ok(await api('/metal/work-order/number/reserve',token,'POST',{requestId})).data;
const same=randomUUID();const repeats=await Promise.all(Array.from({length:8},()=>reserve(same)));assert.equal(new Set(repeats).size,1);
const numbers=await Promise.all(Array.from({length:12},()=>reserve(randomUUID())));assert.equal(new Set(numbers).size,12);assert(!numbers.includes(repeats[0]));numbers.forEach(n=>assert.match(n,/^WO[0-9]{14}$/));
assert.notEqual((await api('/metal/work-order/number/reserve',token,'POST',{requestId:'invalid'})).code,200);
console.log('PASS: concurrent reservations unique; same UUID idempotent; invalid UUID rejected');
