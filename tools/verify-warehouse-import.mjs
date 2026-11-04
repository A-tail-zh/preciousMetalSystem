import assert from 'node:assert/strict'
import{writeFileSync,readFileSync}from'node:fs'
import{execFileSync}from'node:child_process'
import{api,login,ok,sql}from'./local-test-client.mjs'
import{warehouseFixture}from'./warehouse-test-fixture.mjs'
const f=await warehouseFixture()
try{
 const response=await fetch('http://127.0.0.1:8080/metal/warehouse-order/template',{headers:{Authorization:'Bearer '+f.admin}});assert.equal(response.status,200);writeFileSync('.runtime/warehouse-template.xlsx',Buffer.from(await response.arrayBuffer()))
 execFileSync('python',['tools/build-warehouse-import-fixture.py','.runtime/warehouse-template.xlsx','.runtime/warehouse-import.xlsx',f.mat1])
 const body=new FormData();body.append('factoryId',f.factoryA);body.append('warehouseId',f.warehouse);body.append('type','RAW_IN');body.append('file',new Blob([readFileSync('.runtime/warehouse-import.xlsx')]),'warehouse-import.xlsx');const before=sql('SELECT COUNT(*) FROM metal_warehouse_order WHERE factory_id='+f.factoryA);const data=ok(await(await fetch('http://127.0.0.1:8080/metal/warehouse-order/import',{method:'POST',headers:{Authorization:'Bearer '+f.admin},body})).json()).data;assert.equal(data[0].netWeight,'12.345678');assert.equal(sql('SELECT COUNT(*) FROM metal_warehouse_order WHERE factory_id='+f.factoryA),before);await f.post('RAW_IN',data);const rows=ok(await api('/metal/inventory/list?factoryId='+f.factoryA,f.admin)).rows;assert.equal(rows[0].netWeight,'12.345678');
 const ordinary=await login(f.phoneB,f.password);const denied=await api('/metal/warehouse/options/warehouses?factoryId='+f.factoryA,ordinary);assert.equal(denied.code,403);await api('/logout',ordinary,'POST');console.log('PASS Excel模板下载、导入精度、只填充不自动过账及确认后入库');writeFileSync('docs/warehouse-import-verification.json',JSON.stringify({timestamp:new Date().toISOString(),passed:['Excel模板下载','六位小数导入','导入不自动过账','确认后真实入库','普通账号独立权限校验']},null,2))
}finally{await f.cleanup()}
