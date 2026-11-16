import assert from 'node:assert/strict'
import {randomInt,randomBytes} from 'node:crypto'
import {createRequire} from 'node:module'
import {warehouseFixture} from './warehouse-test-fixture.mjs'
import {api,ok,detail,login} from './local-test-client.mjs'
const {chromium}=createRequire(new URL('../frontend/package.json',import.meta.url))('playwright')
const f=await warehouseFixture();let token,browser,count=0
const check=(value,message)=>{assert.ok(value,message);count++}
const create=async(path,body,group)=>{const r=ok(await api(path,f.admin,'POST',body));const id=String(r.id??r.factoryId);f.made[group].push(id);return id}
try{
 const factory=await create('/metal/factory',{factoryName:f.prefix+'独立新厂',area:'1',address:'隔离验证'},'factories')
 check((await detail('/metal/factory',factory,f.admin)).productionLineIds.length===0,'empty factory')
 const line=await create('/metal/production-line',{code:'BOUND_'+f.suffix,name:f.prefix+'独立绑定线',factoryId:factory,productIds:[]},'lines')
 check((await detail('/metal/production-line',line,f.admin)).factoryId===factory,'line selects factory')
 let current=await detail('/metal/factory',factory,f.admin)
 ok(await api('/metal/factory',f.admin,'PUT',{factoryId:factory,version:current.version,factoryName:current.factoryName,area:current.area,address:'修改地址'}))
 check((await detail('/metal/production-line',line,f.admin)).factoryId===factory,'omitted lines preserve binding')
 current=await detail('/metal/factory',factory,f.admin)
 ok(await api('/metal/factory',f.admin,'PUT',{...current,productionLineIds:[]}))
 check((await detail('/metal/production-line',line,f.admin)).factoryId===factory,'empty lines preserve binding')
 current=await detail('/metal/factory',factory,f.admin)
 check((await api('/metal/factory',f.admin,'PUT',{...current,address:'不得落库',productionLineIds:[line]})).code!==200,'legacy binding rejected')
 check((await detail('/metal/factory',factory,f.admin)).address==='修改地址','rejected request atomic')
 check((await api('/metal/production-line',f.admin,'POST',{code:'NO_FACTORY_'+f.suffix,name:f.prefix+'缺厂拒绝',productIds:[]})).code!==200,'factory required')
 const menus=ok(await api('/metal/options/menus',f.admin)).data
 const perms=['metal:factory:list','metal:factory:edit','metal:factory:add','metal:factory:remove','metal:production-line:list','metal:production-line:add','metal:production-line:edit','metal:work-order:list','metal:work-order:edit','metal:warehouse:list','metal:warehouse:edit','metal:inventory:list','metal:inventory:adjust']
 const selected=new Set(menus.filter(m=>perms.includes(m.perms)).map(m=>String(m.id)))
 for(const id of [...selected]){let parent=menus.find(m=>String(m.id)===id)?.parentId;while(parent&&String(parent)!=='0'){selected.add(String(parent));parent=menus.find(m=>String(m.id)===String(parent))?.parentId}}
 const role=await create('/system/role',{roleName:f.prefix+'仅编辑上下文角色',factoryId:f.factoryA,status:'0',roleSort:1,menuIds:[...selected]},'roles')
 const phone=String(randomInt(13000000000,13999999999)),password='Test'+randomBytes(12).toString('hex')
 await create('/system/user',{nickName:f.prefix+'隔离权限人员',phonenumber:phone,password,factoryId:f.factoryA,roleIds:[role],status:'0'},'users')
 token=await login(phone,password)
 check((await api('/metal/factory/'+f.factoryA,token)).code===403,'generic query stays protected')
 check((await api('/metal/factory/'+f.factoryA+'/edit-context',token)).code===200,'edit-only context allowed')
 check((await api('/metal/factory/'+f.factoryB+'/edit-context',token)).code===403,'edit context factory isolation')
 check((await api('/metal/production-line/'+f.lineA+'/edit-context',token)).code===200,'line edit-only context')
 check((await api('/metal/production-line/'+f.lineB+'/edit-context',token)).code===403,'line edit context isolation')
 check((await api('/metal/work-order/'+f.wo,token)).code===403,'work-order generic query protected')
 const orderContext=await api('/metal/work-order/'+f.wo+'/edit-context',token)
 check(orderContext.code===200&&!('reviews' in orderContext.data)&&!('reports' in orderContext.data),'work-order edit-only context excludes review/report history')
 check((await api('/metal/warehouse/'+f.warehouse,token)).code===403,'warehouse generic query protected')
 check((await api('/metal/warehouse/'+f.warehouse+'/edit-context',token)).code===200,'warehouse edit-only context')
 const inbound=await f.post('RAW_IN',[{category:'RAW',referenceId:f.mat1,netWeight:'10',quantity:0}])
 const inventory=(await detail('/metal/warehouse-order',inbound,f.admin)).lines[0].inventoryId
 check((await api('/metal/inventory/'+inventory,token)).code===403,'inventory generic query protected')
 const adjustContext=await api('/metal/inventory/'+inventory+'/edit-context',token)
 check(adjustContext.code===200&&!('ledger' in adjustContext.data),'adjust-only context excludes ledger')
 check((await api('/metal/factory',token,'POST',{factoryName:f.prefix+'禁止越权建厂',area:'1',address:'隔离验证'})).code===403,'admin-only create remains protected')
 check((await api('/metal/factory/'+factory,token,'DELETE')).code===403,'admin-only delete remains protected')
 const options=ok(await api('/metal/options/factories',token)).data
 check(options.length===1&&options[0].id===f.factoryA,'minimal factory options scoped')
 check((await api('/metal/production-line',token,'POST',{code:'CROSS_'+f.suffix,name:f.prefix+'跨厂拒绝',factoryId:f.factoryB,productIds:[]})).code===403,'cross-factory line create denied')
 browser=await chromium.launch({channel:'chrome',headless:true})
 const page=await browser.newPage({viewport:{width:1440,height:1000}});await page.context().addCookies([{name:'Admin-Token',value:token,url:'http://127.0.0.1:5173'}])
 await page.goto('http://127.0.0.1:5173/metal/factory');await page.locator('.factory-table').waitFor()
 check(await page.getByRole('button',{name:'添加',exact:true}).count()===0,'nonadmin create hidden')
 check(await page.getByRole('button',{name:'删除',exact:true}).count()===0,'nonadmin delete hidden')
 await page.getByRole('row').filter({hasText:f.prefix+'厂A'}).getByRole('button',{name:'编辑',exact:true}).click()
 await page.getByRole('dialog').waitFor();check(await page.getByRole('dialog').getByLabel('工厂名称',{exact:true}).inputValue()===f.prefix+'厂A','real edit-only UI loads')
 await page.screenshot({path:'.runtime/factory-permission-edit-only.png',fullPage:true})
 await page.close()
 console.log(JSON.stringify({passed:count,failed:0,scenario:'factory independent creation and real least-privilege editor',cleaned:'see final cleanup'},null,2))
}finally{await browser?.close();if(token)await api('/logout',token,'POST');await f.cleanup();console.log('isolated fixtures cleaned')}
