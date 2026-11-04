import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {writeFileSync} from 'node:fs';
if(!process.env.TEST_MYSQL_PORT||process.env.TEST_MYSQL_PORT==='3308'||!process.env.TEST_REDIS_PORT||process.env.TEST_REDIS_PORT==='6380'||!process.env.TEST_API_BASE_URL)throw Error('Explicit isolated MySQL, Redis and API required');
const {api,ok,detail,sql,login}=await import('./local-test-client.mjs');
const {warehouseFixture}=await import('./warehouse-test-fixture.mjs');
const passed=[],tokens=[],triggers=[];let f,cleaned=false;
const pass=name=>{passed.push(name);console.log('PASS '+name)};
const rejected=(r,message)=>{assert.notEqual(r.code,200,r.msg);if(message)assert.match(r.msg,message)};
try{
 f=await warehouseFixture({completionPolicy:'SINGLE_OUTPUT_RETURN'});
 const create=async(path,body,group)=>{const id=String(ok(await api(path,f.admin,'POST',body)).id);f.made[group].push(id);return id};
 const material=async(type,metal='1')=>create('/metal/material',{code:'MT_'+f.made.materials.length+'_'+f.suffix,materialType:type,metalTypeId:metal},'materials');
 for(const [old,type]of Object.entries({'铱粉':'粉料','铱块':'块料','旧铱杆':'旧料','旧铱坩埚':'旧料'})){
  const id=await material(old,null),row=await detail('/metal/material',id,f.admin);assert.equal(row.materialType,type);assert.equal(row.metalTypeId,'1');
 }
 pass('all four legacy request types normalize without merging IDs');
 for(const body of [{materialType:'粉料'},{materialType:'粉料',metalTypeId:'999999999'},{materialType:'未知',metalTypeId:'1'}])rejected(await api('/metal/material',f.admin,'POST',{code:'INVALID_'+f.suffix,...body}));
 pass('canonical writes reject missing/invalid metal and unknown type');
 const metal=await create('/metal/metal-types',{name:f.prefix+'测试金属'},'metalTypes');
 rejected(await api('/metal/material',f.admin,'POST',{code:'LEGACY_BAD_'+f.suffix,materialType:'铱粉',metalTypeId:metal}),/旧物料类型/);
 const longMetal=await create('/metal/metal-types',{name:(f.prefix+'长').padEnd(100,'金')},'metalTypes');const longMaterial=await material('粉料',longMetal);
 const foreign=await material('粉料',metal),iridium=await material('块料'),mutable=await material('粉料');
 let row=await detail('/metal/material',mutable,f.admin);
 ok(await api('/metal/material',f.admin,'PUT',{...row,materialType:'旧料',metalTypeId:metal}));
 assert.equal((await detail('/metal/material',mutable,f.admin)).metalTypeId,metal);
 rejected(await api('/metal/material',f.admin,'PUT',{...row,specification:'stale'}),/已被修改/);pass('unreferenced classification can change and stale versions are rejected');
 rejected(await api('/metal/material',f.admin,'POST',{code:row.code,materialType:'粉料',metalTypeId:'1'}),/已存在/);pass('material codes remain globally unique');
 const second=String(ok(await api('/metal/warehouse',f.admin,'POST',{requestId:randomUUID(),factoryId:f.factoryA,name:f.prefix+'二仓',sortOrder:1,status:'0'})).id);
 const raw=(id,weight='20')=>({category:'RAW',referenceId:id,netWeight:weight,quantity:0});
 const order=(type,warehouseId,lines,extra={})=>({requestId:randomUUID(),factoryId:f.factoryA,warehouseId,type,orderDate:'2026-11-02',operatorIds:[f.ownerA],keeperId:f.ownerA,recipientId:f.ownerA,lines,...extra});
 const post=async body=>String(ok(await api('/metal/warehouse-order',f.admin,'POST',body)).id);
 const foreignRequest=order('RAW_IN',second,[raw(foreign)]),foreignOrder=await post(foreignRequest);
 const foreignDoc=await detail('/metal/warehouse-order',foreignOrder,f.admin),foreignStock=String(foreignDoc.lines[0].inventoryId);
 assert.equal(foreignDoc.lines[0].name,f.prefix+'测试金属粉料');assert.equal(await post(foreignRequest),foreignOrder);
 assert.equal(Number((await detail('/metal/inventory',foreignStock,f.admin)).netWeight),20);pass('non-iridium ordinary inbound uses metal/type name; replay does not double stock');
 const longOrder=await post(order('RAW_IN',second,[raw(longMaterial,'1')]));assert.equal((await detail('/metal/warehouse-order',longOrder,f.admin)).lines[0].name.length,102);pass('100-character metal names fit new inventory and document snapshots');
 const iridiumOrder=await post(order('RAW_IN',f.warehouse,[raw(iridium)]));const iridiumStock=String((await detail('/metal/warehouse-order',iridiumOrder,f.admin)).lines[0].inventoryId);
 row=await detail('/metal/material',foreign,f.admin);assert.equal(row.classificationLocked,true);
 for(const change of [{materialType:'旧料'},{metalTypeId:'1'}])rejected(await api('/metal/material',f.admin,'PUT',{...row,...change}),/已有库存或仓储引用/);
 ok(await api('/metal/material',f.admin,'PUT',{...row,specification:'edited specification'}));pass('referenced classification is locked but specification remains editable');
 const inv=await detail('/metal/inventory',foreignStock,f.admin);
 ok(await api(`/metal/inventory/${foreignStock}/adjust`,f.admin,'POST',{requestId:randomUUID(),version:inv.version,operatorId:f.ownerA,keeperId:f.ownerA,deltaWeight:'1',deltaQuantity:0,reason:'隔离物料调整验收'}));
 const exchange=await post(order('EXCHANGE_OUT',second,[{inventoryId:foreignStock,netWeight:'1',quantity:0}]));
 const exchangeDoc=await detail('/metal/warehouse-order',exchange,f.admin);ok(await api(`/metal/warehouse-order/${exchange}/reverse`,f.admin,'POST',{version:exchangeDoc.version,reason:'隔离冲销验收'}));pass('non-iridium stock adjustment, ordinary outbound and reversal remain usable');
 const opening=order('OPENING',second,[raw(mutable,'1')]);ok(await api('/metal/inventory/opening',f.admin,'POST',opening));pass('non-iridium opening stock is allowed');
 const production={workOrderId:f.wo,itemId:f.item.id,processId:f.processA};
 const counts=()=>sql(`SELECT (SELECT COUNT(*) FROM metal_warehouse_order WHERE factory_id=${f.factoryA}),(SELECT COUNT(*) FROM metal_process_batch WHERE factory_id=${f.factoryA}),(SELECT COUNT(*) FROM metal_stock_ledger l JOIN metal_inventory i ON i.inventory_id=l.inventory_id WHERE i.factory_id=${f.factoryA}),(SELECT SUM(net_weight) FROM metal_inventory WHERE factory_id=${f.factoryA})`);
 let before=counts();rejected(await api('/metal/warehouse-order',f.admin,'POST',order('PRODUCTION_OUT',second,[{inventoryId:foreignStock,netWeight:'1',quantity:0}],production)),/其他金属物料/);assert.equal(counts(),before);pass('direct production rejects non-iridium without stock, ledger or batch residue');
 const outbound={requestId:randomUUID(),factoryId:f.factoryA,type:'PRODUCTION_OUT',orderDate:'2026-11-02',operatorIds:[],recipientId:f.ownerA,keeperId:f.ownerA,...production,groups:[{warehouseId:f.warehouse,lines:[{inventoryId:iridiumStock,netWeight:'2',quantity:0}]},{warehouseId:second,lines:[{inventoryId:foreignStock,netWeight:'1',quantity:0}]}]};
 const preview=ok(await api('/metal/warehouse-order/outbound-submissions/preview',f.admin,'POST',outbound)).data;assert.ok(preview.fieldErrors.some(e=>e.message.includes('其他金属物料')));
 rejected(await api('/metal/warehouse-order/outbound-submissions',f.admin,'POST',outbound));assert.equal(counts(),before);pass('multi-warehouse preview and save reject non-iridium and roll back every warehouse');
 const candidates=ok(await api('/metal/warehouse/options/stocks?'+new URLSearchParams({factoryId:f.factoryA,warehouseId:second,...production}),f.admin));
 assert.ok(!candidates.data.some(s=>String(s.id)===foreignStock));pass('production stock candidates omit non-iridium');
 const legacy=String(ok(await api('/metal/warehouse-order',f.admin,'POST',order('PRODUCTION_OUT',f.warehouse,[{inventoryId:iridiumStock,netWeight:'10',quantity:0}],production))).id);
 const batch=String((await detail('/metal/warehouse-order',legacy,f.admin)).batchId);
 before=counts();rejected(await api('/metal/warehouse-order',f.admin,'POST',order('PRODUCTION_OUT',second,[{inventoryId:foreignStock,netWeight:'1',quantity:0}],{...production,batchId:batch,batchVersion:(await detail('/metal/process-batch',batch,f.admin)).version})),/其他金属物料/);assert.equal(counts(),before);pass('existing batch append cannot introduce another metal');
 const makeImportFile=row=>execFileSync('python',['-X','utf8','-c',"from openpyxl import Workbook;from io import BytesIO;import sys,json;v=json.loads(sys.stdin.read());w=Workbook();s=w.active;s.append(v['headers']);s.append(v['row']);b=BytesIO();w.save(b);sys.stdout.buffer.write(b.getvalue())"],{input:JSON.stringify({headers:['库存ID','资料ID','原出库明细ID','分类','批次号','净重(g)','数量','毛重(g)','杂质(g)','纯度(%)','单位名称','用途','备注'],row})});
 const importFile=makeImportFile([foreignStock,null,null,'RAW',null,'1',0]);
 const importForm=new FormData();importForm.set('file',new Blob([importFile]),'material.xlsx');for(const [k,v]of Object.entries({factoryId:f.factoryA,warehouseId:second,type:'PRODUCTION_OUT',...production,batchId:batch}))importForm.set(k,String(v));
 const imported=await(await fetch(process.env.TEST_API_BASE_URL+'/metal/warehouse-order/import',{method:'POST',headers:{Authorization:'Bearer '+f.admin},body:importForm})).json();rejected(imported,/其他金属物料/);pass('legacy XLSX production import cannot bypass the metal guard');
 const rawImportForm=new FormData();rawImportForm.set('file',new Blob([makeImportFile([null,foreign,null,'RAW',null,'1',0])]),'raw-material.xlsx');for(const [k,v]of Object.entries({factoryId:f.factoryA,warehouseId:second,type:'RAW_IN'}))rawImportForm.set(k,String(v));
 const rawPreview=ok(await(await fetch(process.env.TEST_API_BASE_URL+'/metal/warehouse-order/import',{method:'POST',headers:{Authorization:'Bearer '+f.admin},body:rawImportForm})).json()).data[0];assert.equal(rawPreview.materialType,'粉料');assert.equal(rawPreview.metalTypeId,metal);assert.equal(rawPreview.name,f.prefix+'测试金属粉料');pass('legacy raw XLSX import resolves canonical material and metal metadata by ID');
 const filtered=ok(await api('/metal/material/list?'+new URLSearchParams({metalTypeId:metal,materialType:'粉料',pageSize:'100'}),f.admin)).rows;assert.ok(filtered.some(r=>r.id===foreign));assert.ok(filtered.every(r=>r.metalTypeId===metal&&r.materialType==='粉料'));pass('material list filters metal and type independently');
 const template=await fetch(process.env.TEST_API_BASE_URL+'/metal/warehouse-order/template?type=RAW_IN',{headers:{Authorization:'Bearer '+f.admin}});assert.equal(template.status,200);
 const headers=execFileSync('python',['-X','utf8','-c',"from openpyxl import load_workbook;from io import BytesIO;import sys,json;w=load_workbook(BytesIO(sys.stdin.buffer.read()));print(json.dumps(list(next(w['ID参考'].values)),ensure_ascii=True))"],{input:Buffer.from(await template.arrayBuffer()),encoding:'utf8'});assert.deepEqual(JSON.parse(headers),['ID','编号','名称','规格型号','物料类型','金属类型']);pass('raw template reference sheet includes classification without changing import columns');
 // Existing APIs keep ownership and role checks; only the metal directory is newly available.
 const menus=ok(await api('/metal/options/menus',f.admin)).data;
 for(const mode of ['query','add','edit']){
  const wanted=new Set(['metal:material:list','metal:material:'+mode]),ids=new Set(menus.filter(m=>wanted.has(m.perms)).map(m=>m.id));
  for(const id of [...ids]){let parent=menus.find(m=>m.id===id)?.parentId;while(parent&&parent!=='0'){ids.add(parent);parent=menus.find(m=>m.id===parent)?.parentId}}
  const role=await create('/system/role',{roleName:f.prefix+'物料权限'+mode,factoryId:f.factoryA,roleSort:1,status:'0',menuIds:[...ids]},'roles');
  const phone='135'+String(Date.now()).slice(-8),password='Material'+randomUUID().replaceAll('-','');
  await create('/system/user',{phonenumber:phone,nickName:f.prefix+'物料人员'+mode,factoryId:f.factoryA,roleIds:[role],status:'0',password},'users');const token=await login(phone,password);tokens.push(token);
  ok(await api('/metal/metal-types',token));rejected(await api('/metal/quote-rule/list',token));
  const response=await api('/metal/metal-types',token,'POST',{name:f.prefix+'权限金属'+mode});
  if(mode==='query')rejected(response);else{ok(response);f.made.metalTypes.push(String(response.id));rejected(await api('/metal/metal-types',token,'POST',{name:f.prefix+'权限金属'+mode}),/已存在/)}
 }
 pass('query/add/edit material roles have exact shared-metal access without quote-rule access');
 rejected(await api('/metal/metal-types',f.admin,'POST',{name:'   '}));pass('blank shared metal names are rejected');
 const waitForSleep=async()=>{const deadline=Date.now()+5000;while(Date.now()<deadline){if(Number(sql("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE STATE='User sleep'"))>0)return;await new Promise(r=>setTimeout(r,25))}throw Error('concurrency trigger did not start')};
 const inboundFirst=await material('粉料'),inboundFirstRow=await detail('/metal/material',inboundFirst,f.admin);
 const insertTrigger='mt_insert_'+f.suffix.replaceAll('_','');triggers.push(insertTrigger);
 sql(`CREATE TRIGGER ${insertTrigger} BEFORE INSERT ON metal_inventory FOR EACH ROW DO IF(NEW.reference_id=${inboundFirst} AND NEW.category='RAW',SLEEP(0.8),0)`);
 const inboundPending=api('/metal/warehouse-order',f.admin,'POST',order('RAW_IN',second,[raw(inboundFirst,'1')]));await waitForSleep();
 const blockedEdit=api('/metal/material',f.admin,'PUT',{...inboundFirstRow,materialType:'块料'});
 ok(await inboundPending);rejected(await blockedEdit,/已有库存或仓储引用/);sql(`DROP TRIGGER ${insertTrigger}`);triggers.pop();pass('first inbound holding the material lock prevents a concurrent classification change');
 const editFirst=await material('粉料'),editFirstRow=await detail('/metal/material',editFirst,f.admin);
 const updateTrigger='mt_update_'+f.suffix.replaceAll('_','');triggers.push(updateTrigger);
 sql(`CREATE TRIGGER ${updateTrigger} BEFORE UPDATE ON metal_material FOR EACH ROW DO IF(NEW.material_id=${editFirst},SLEEP(0.8),0)`);
 const editPending=api('/metal/material',f.admin,'PUT',{...editFirstRow,materialType:'块料'});await waitForSleep();
 const waitingInbound=api('/metal/warehouse-order',f.admin,'POST',order('RAW_IN',second,[raw(editFirst,'1')]));ok(await editPending);
 const updatedInbound=ok(await waitingInbound);assert.equal((await detail('/metal/warehouse-order',updatedInbound.id,f.admin)).lines[0].name,'铱块料');sql(`DROP TRIGGER ${updateTrigger}`);triggers.pop();pass('classification edit holding the lock makes first inbound use the committed new type');
 const ui=JSON.parse(execFileSync('python',['-X','utf8','tools/verify-material-ui.py'],{input:JSON.stringify({token:tokens[1],suffix:f.suffix,baseUrl:process.env.TEST_FRONTEND_BASE_URL||'http://127.0.0.1:5174'}),encoding:'utf8'}));f.made.materials.push(...ui.ids);assert.equal(ui.passed,4);pass('real browser creates materials at 1920/1440/768/390 with a material-only add account');
 const ctx=async b=>ok(await api(`/metal/process-batch/${b}/completion-context`,f.admin)).data;
 const output=await f.post('SEMI_IN',[{category:'SEMI',referenceId:f.productA,netWeight:'8',quantity:5}],{batchId:batch});
 let context=await ctx(batch);ok(await api(`/metal/process-batch/${batch}/confirm-return`,f.admin,'POST',{requestId:randomUUID(),version:context.version,mode:'RETURNED',physicalChecked:true,reason:'分类兼容还料验收',documents:[{type:'RAW_RETURN',warehouseId:f.warehouse,orderDate:'2026-11-02',operatorIds:[f.ownerA],keeperId:f.ownerA,reason:'剩余原料还库',lines:[{sourceLineId:context.sources[0].id,netWeight:'1',quantity:0}]}]}));
 context=await ctx(batch);assert.equal(context.status,'SETTLED');
 const semiStock=(await detail('/metal/warehouse-order',output,f.admin)).lines[0].inventoryId,downstream=await f.createBatch(f.processB,5);
 await f.post('PRODUCTION_OUT',[{inventoryId:semiStock,netWeight:'8',quantity:5}],{batchId:downstream});await f.post('FINISHED_IN',[{category:'FINISHED',referenceId:f.productA,netWeight:'6',quantity:5}],{batchId:downstream});
 let down=await ctx(downstream);before=counts();const recovery=await api(`/metal/process-batch/${downstream}/confirm-return`,f.admin,'POST',{requestId:randomUUID(),version:down.version,mode:'RETURNED',physicalChecked:true,reason:'禁止跨金属回收',documents:[{type:'PROCESS_RAW_RETURN',warehouseId:second,orderDate:'2026-11-02',operatorIds:[f.ownerA],keeperId:f.ownerA,reason:'跨金属回收应拒绝',lines:[raw(foreign,'1')]}]});rejected(recovery,/其他金属物料/);assert.equal(counts(),before);pass('return confirmation cannot convert iridium process residue into another metal');
 ok(await api(`/metal/process-batch/${downstream}/confirm-return`,f.admin,'POST',{requestId:randomUUID(),version:down.version,mode:'NONE',physicalChecked:true,reason:'无余料确认',documents:[]}));down=await ctx(downstream);ok(await api(`/metal/process-batch/${downstream}/reverse`,f.admin,'POST',{version:down.version,reason:'下游整体冲销'}));
 ok(await api(`/metal/process-batch/${batch}/reverse`,f.admin,'POST',{version:context.version,reason:'分类兼容整批冲销'}));
 assert.equal((await detail('/metal/warehouse-order',legacy,f.admin)).status,'VOID');pass('iridium production, output, return settlement and whole-batch reversal still work');
 const zero=await material('粉料'),zeroOrder=await post(order('RAW_IN',second,[raw(zero,'1')]));const zeroDoc=await detail('/metal/warehouse-order',zeroOrder,f.admin);
 ok(await api(`/metal/warehouse-order/${zeroOrder}/reverse`,f.admin,'POST',{version:zeroDoc.version,reason:'零余额引用验收'}));row=await detail('/metal/material',zero,f.admin);assert.equal(row.classificationLocked,true);rejected(await api('/metal/material',f.admin,'PUT',{...row,materialType:'块料'}),/已有库存或仓储引用/);pass('zero balance and reversed history continue to lock classification');
}finally{
 for(const trigger of triggers)sql(`DROP TRIGGER IF EXISTS ${trigger}`);
 for(const token of tokens)await api('/logout',token,'POST');
 if(f){await f.cleanup();cleaned=true;console.log('CLEANED registered fixture; audit retained')}
 writeFileSync(new URL('../.runtime/material-20261102/live-result.json',import.meta.url),JSON.stringify({passed:passed.length,checks:passed,cleaned,auditRetained:true},null,2));
}
