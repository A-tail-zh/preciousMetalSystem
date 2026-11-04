import test from 'node:test'
import assert from 'node:assert/strict'
import {api,login,ok,detail} from '../../tools/local-test-client.mjs'

test('四模块真实接口 CRUD、关联限制和失败更新的数据一致性', async t => {
  const token = await login(), suffix = `${Date.now()}`, records = []
  const create = async (path,body) => {
    const r = ok(await api(path,token,'POST',body)), id = String(r.id ?? r.factoryId)
    records.push({path,id}); return id
  }
  const rejected = async (path,method,body) => {
    const r = await api(path,token,method,body)
    assert.notEqual(r.code,200,`Unexpected success: ${method} ${path}`)
    return r
  }
  const deleted = async (path,id) => {
    ok(await api(`${path}/${id}`,token,'DELETE'))
    records.splice(records.findIndex(r=>r.path===path&&r.id===id),1)
    await rejected(`${path}/${id}`,'GET')
    assert.ok(!ok(await api(`${path}/list?pageSize=100`,token)).rows.some(r=>String(r.id)===id))
  }
  try {
    const processPath='/metal/process', productPath='/metal/product', materialPath='/metal/material', linePath='/metal/production-line'
    let process,product,material,line,factory
    await t.test('工序：新增、列表、详情、编辑、旧版本拒绝',async()=>{
      process=await create(processPath,{name:`CRUD工序_${suffix}`,processType:1,description:'原描述'})
      const d=await detail(processPath,process,token); assert.equal(d.description,'原描述')
      assert.ok(ok(await api(`${processPath}/list?pageSize=100`,token)).rows.some(r=>String(r.id)===process))
      ok(await api(processPath,token,'PUT',{...d,description:'新描述',processType:2}))
      const changed=await detail(processPath,process,token); assert.equal(changed.description,'新描述'); assert.equal(changed.processType,2); assert.equal(changed.version,d.version+1)
      await rejected(processPath,'PUT',{...d,description:'不应保存'})
      assert.equal((await detail(processPath,process,token)).description,'新描述')
      await rejected(processPath,'POST',{name:`非法工序_${suffix}`,processType:10})
    })
    await t.test('物料：新增、列表、详情、编辑、重复编码、非法类型',async()=>{
      material=await create(materialPath,{name:`CRUD物料_${suffix}`,code:`CM${suffix}`,materialType:'铱粉',specification:'规格一'})
      const d=await detail(materialPath,material,token); assert.equal(d.specification,'规格一')
      assert.ok(ok(await api(`${materialPath}/list?pageSize=100`,token)).rows.some(r=>String(r.id)===material))
      ok(await api(materialPath,token,'PUT',{...d,specification:'规格二',materialType:'铱块'}))
      assert.equal((await detail(materialPath,material,token)).specification,'规格二')
      await rejected(materialPath,'PUT',{...d,specification:'旧版本'})
      await rejected(materialPath,'POST',{name:`重复物料_${suffix}`,code:d.code,materialType:'铱粉'})
      await rejected(materialPath,'POST',{name:`非法物料_${suffix}`,code:`IM${suffix}`,materialType:'非法类型'})
    })
    await t.test('产品：新增工序关联、列表、详情、编辑、尺寸校验与失败更新',async()=>{
      product=await create(productPath,{name:`CRUD产品_${suffix}`,code:`CP${suffix}`,category:'铱环',unit:'件',dimensions:{outerDiameter:'12',innerDiameter:'10',height:'2'},processes:[{processId:process,loss:'0.123456'}]})
      const d=await detail(productPath,product,token); assert.equal(String(d.processes[0].processId),process); assert.equal(d.processes[0].loss,'0.123456')
      assert.ok(ok(await api(`${productPath}/list?pageSize=100`,token)).rows.some(r=>String(r.id)===product))
      ok(await api(productPath,token,'PUT',{...d,weight:'2.123456',processes:[{processId:process,loss:'0.5'}]}))
      const changed=await detail(productPath,product,token); assert.equal(changed.weight,'2.123456'); assert.equal(changed.processes[0].loss,'0.500000')
      await rejected(productPath,'PUT',{...d,weight:'99'})
      await rejected(productPath,'PUT',{...changed,dimensions:{outerDiameter:'9',innerDiameter:'10',height:'2'}})
      await rejected(productPath,'PUT',{...changed,processes:[{processId:'9223372036854775807',loss:'1'}]})
      const after=await detail(productPath,product,token); assert.equal(after.version,changed.version); assert.deepEqual(after.processes,changed.processes); assert.deepEqual(after.dimensions,changed.dimensions)
      await rejected(`${processPath}/${process}`,'DELETE')
      await detail(processPath,process,token)
    })
    await t.test('产线：新增产品关联、列表、详情、编辑、删除关联限制',async()=>{
      factory=await create('/metal/factory',{factoryName:`CRUD厂_${suffix}`,area:'1',address:'临时测试地址',productionLineIds:[]})
      line=await create(linePath,{name:`CRUD产线_${suffix}`,code:`CL${suffix}`,factoryId:factory,productIds:[product]})
      const d=await detail(linePath,line,token); assert.equal(String(d.factoryId),factory); assert.deepEqual(d.productIds,[product])
      assert.ok(ok(await api(`${linePath}/list?pageSize=100`,token)).rows.some(r=>String(r.id)===line))
      ok(await api(linePath,token,'PUT',{...d,name:`CRUD产线修改_${suffix}`}))
      const changed=await detail(linePath,line,token); assert.equal(changed.name,`CRUD产线修改_${suffix}`)
      await rejected(linePath,'PUT',{...d,name:'旧版本'})
      await rejected(linePath,'PUT',{...changed,productIds:[product,product]})
      await rejected(linePath,'PUT',{...changed,productIds:['9223372036854775807']})
      const after=await detail(linePath,line,token); assert.deepEqual(after.productIds,[product]); assert.equal(after.version,changed.version)
      await rejected(`${linePath}/${line}`,'DELETE'); await rejected(`${productPath}/${product}`,'DELETE')
    })
    await t.test('解除引用后：产线、产品、物料、工序删除成功且查询不可见',async()=>{
      const l=await detail(linePath,line,token); ok(await api(linePath,token,'PUT',{...l,productIds:[]}))
      const f=await detail('/metal/factory',factory,token); ok(await api('/metal/factory',token,'PUT',{...f,productionLineIds:[]}))
      await deleted(linePath,line); await deleted(productPath,product); await deleted(materialPath,material); await deleted(processPath,process)
    })
  } finally {
    const cleanupErrors=[]
    for(const path of ['/metal/production-line','/metal/factory']) for(const r of records.filter(r=>r.path===path)) {
      try {const d=await detail(path,r.id,token);ok(await api(path,token,'PUT',{...d,...(path==='/metal/factory'?{productionLineIds:[]}:{productIds:[]})}))} catch(e){cleanupErrors.push(e.message)}
    }
    for(const path of ['/metal/production-line','/metal/product','/metal/material','/metal/process','/metal/factory']) for(const r of records.filter(r=>r.path===path)) {
      try {ok(await api(`${path}/${r.id}`,token,'DELETE'))} catch(e){cleanupErrors.push(`${path}/${r.id}: ${e.message}`)}
    }
    await api('/logout',token,'POST')
    assert.deepEqual(cleanupErrors,[],'Test fixture cleanup failed')
  }
})
