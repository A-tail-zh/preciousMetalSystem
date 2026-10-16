import assert from 'node:assert/strict';import{randomUUID}from'node:crypto';import{api,login,ok}from'./local-test-client.mjs';
const token=await login();const reserve=async(requestId,kind)=>ok(await api('/metal/warehouse-order/number/reserve',token,'POST',{requestId,kind})).data;
for(const kind of ['OUT','IN','RET']){const id=randomUUID(),same=await Promise.all(Array.from({length:5},()=>reserve(id,kind)));assert.equal(new Set(same).size,1);const distinct=await Promise.all(Array.from({length:6},()=>reserve(randomUUID(),kind)));assert.equal(new Set(distinct).size,6);assert(!distinct.includes(same[0]));assert(same[0].startsWith(kind));}
console.log('PASS: OUT/IN/RET并发编号唯一，同请求重复申请保持编号');
