import {test,expect,type Page,type Locator} from '@playwright/test'
import {receiptTypeFor} from '../src/views/metal/storage-orders/model'

test('receipt types derive from frozen output categories and never guess unknown categories',()=>{
 expect(receiptTypeFor('RAW')).toBe('PROCESS_RAW_IN');expect(receiptTypeFor('SEMI')).toBe('SEMI_IN');expect(receiptTypeFor('PRODUCT')).toBe('PRODUCT_IN');expect(receiptTypeFor(null)).toBeUndefined()
})

async function choose(page:Page,picker:Locator,name:string){
 await expect(picker).toBeEnabled();await picker.press('ArrowDown');const id=await picker.getAttribute('aria-controls')
 await page.locator(`[id="${id}"]`).getByRole('option',{name,exact:true}).click()
}

async function fixture(page:Page,baseURL:string,legacy=false,missingBatch=false){
 const mutations:{path:string;body:any}[]=[]
 const executions=[{id:'6',number:'PC6',orderId:'7',orderItemId:'31',orderStepId:'41',processorId:'81',processorName:'加工甲',status:'OPEN',outputCategory:'RAW',productName:'金环',processName:'酸洗',measurementMode:'WEIGHT'},{id:'7',number:'PC7',orderId:'7',orderItemId:'31',orderStepId:'42',processorId:'82',processorName:'加工乙',status:'OPEN',outputCategory:'SEMI',productName:'金环',processName:'融定',measurementMode:'WEIGHT'},{id:'8',number:'PC8',orderId:'7',orderItemId:'31',orderStepId:'43',processorId:'83',processorName:'加工丙',status:'OPEN',outputCategory:'PRODUCT',productName:'金环',processName:'成型',measurementMode:'PIECE'}]
 let doc:any={id:'24',number:'IN_LEGACY',kind:'IN',type:'SEMI_IN',status:'DRAFT',version:0,reviewRound:0,factoryId:'101',creatorId:'1',reviewerId:'2',orderId:'7',executionId:missingBatch?'':'6',processorId:'',orderItemId:'',orderStepId:'',warehouseIds:['11'],date:'2026-11-11',lines:missingBatch?[]:[{id:'21',localId:'L1',warehouseId:'11',productId:'51',code:'OLD',name:'旧半成品明细',category:'SEMI',netWeight:'5.00',measurementMode:'WEIGHT'}]}
 await page.context().addCookies([{name:'Admin-Token',value:'receipt-binding-test',url:baseURL}])
 await page.route('**/*-api/**',async route=>{
  const path=new URL(route.request().url()).pathname.replace(/^\/(dev|prod)-api/,'');let body:any={code:200,data:[],rows:[],total:0}
  if(path==='/getInfo')body={code:200,user:{userId:1,userName:'admin'},roles:['admin'],permissions:['*:*:*']}
  if(path==='/getRouters')body.data=[{path:'/metal',component:'Layout',children:[{path:'storage-orders',component:'metal/storage-orders/index'}]}]
  if(path==='/metal/options/factories')body.data=[{id:'101',name:'本厂'}]
  if(path==='/metal/storage-orders/list')body={code:200,rows:legacy?[doc]:[],total:legacy?1:0}
  if(path==='/metal/storage-orders/24'&&route.request().method()==='GET')body.data=doc
  if(path==='/metal/storage-orders/options')body.data={warehouses:[{id:'11',name:'仓库一',status:'0'}],people:[{id:'2',name:'仓管',canReceive:true,canAccept:true}],productionOrders:[{id:'7',number:'GD7'}],executions,materials:[],products:[],stocks:[],customers:[]}
  if((path==='/metal/storage-orders'||path==='/metal/storage-orders/24')&&route.request().method()!=='GET'){
   const payload=route.request().postDataJSON();mutations.push({path,body:payload});doc={...doc,...payload,id:'24',version:doc.version+1};body.data=doc
  }
  await route.fulfill({json:body})
 })
 await page.goto('/metal/storage-orders')
 await choose(page,page.getByRole('combobox',{name:'所属工厂',exact:true}),'本厂')
 await page.getByRole('tab',{name:'入库单',exact:true}).click()
 if(legacy)await page.getByRole('row').filter({hasText:'IN_LEGACY'}).getByRole('button',{name:'编辑',exact:true}).click()
 else await page.getByRole('button',{name:'添加',exact:true}).click()
 const dialog=page.getByRole('dialog',{name:legacy?'编辑入库单':'添加入库单',exact:true});await expect(dialog).toBeVisible()
 return {dialog,mutations}
}

for(const [id,record,expected] of [['6','历史记录 / 酸洗 / 加工甲 / 6','加工原材料入库'],['7','历史记录 / 融定 / 加工乙 / 7','半成品入库'],['8','历史记录 / 成型 / 加工丙 / 8','成品入库']])test(`processing record ${record} automatically fixes receipt type and linked IDs`,async({page,baseURL})=>{
 const {dialog,mutations}=await fixture(page,baseURL!)
 await choose(page,dialog.getByRole('combobox',{name:'单据类型',exact:true}),'半成品入库')
 await choose(page,dialog.getByRole('combobox',{name:'生产工单',exact:true}),'GD7')
 await choose(page,dialog.getByRole('combobox',{name:'工序加工记录',exact:true}),record)
 const picker=dialog.getByRole('combobox',{name:'单据类型',exact:true});await picker.press('ArrowDown')
 await expect(page.locator(`[id="${await picker.getAttribute('aria-controls')}"]`).getByRole('option')).toHaveText(expected==='半成品入库'?[expected,'加工原材料入库']:[expected]);await picker.press('Escape')
 await expect(dialog.getByRole('textbox',{name:'批次加工人员',exact:true})).toHaveValue(id==='6'?'加工甲':id==='7'?'加工乙':'加工丙')
 await choose(page,dialog.getByRole('combobox',{name:'指定复验人员',exact:true}),'仓管')
 await dialog.getByRole('button',{name:'保存草稿',exact:true}).click();await expect(dialog).not.toBeVisible()
 expect(mutations).toHaveLength(1);const saved=mutations[0].body
 expect(saved.orderItemId).toBe('31');expect(saved.orderStepId).toBe(id==='6'?'41':id==='7'?'42':'43');expect(saved.processorId).toBe(id==='6'?'81':id==='7'?'82':'83')
 expect(saved.type).toBe(receiptTypeFor(id==='6'?'RAW':id==='7'?'SEMI':'PRODUCT'))
})

test('legacy wrong-type draft has a clear warning and can explicitly correct type without rebinding batch',async({page,baseURL})=>{
 const {dialog,mutations}=await fixture(page,baseURL!,true)
 await expect(dialog.getByRole('alert').filter({hasText:'加工原材料入库'}).first()).toBeVisible()
 await expect(dialog.getByRole('combobox',{name:'工序加工记录',exact:true})).toBeDisabled()
 await dialog.getByRole('button',{name:'提交复验',exact:true}).click();expect(mutations).toHaveLength(0)
 await choose(page,dialog.getByRole('combobox',{name:'单据类型',exact:true}),'加工原材料入库')
 await page.getByRole('dialog',{name:'确认变更',exact:true}).getByRole('button',{name:'确定',exact:true}).click()
 await expect(dialog.getByText('旧半成品明细',{exact:false})).toHaveCount(0)
 await dialog.getByRole('button',{name:'保存草稿',exact:true}).click();await expect(dialog).not.toBeVisible()
 expect(mutations).toHaveLength(1);expect(mutations[0].body).toMatchObject({type:'PROCESS_RAW_IN',executionId:'6',orderItemId:'31',orderStepId:'41',processorId:'81',lines:[]})
})

test('legacy draft without a bound batch can select one and save all linked fields',async({page,baseURL})=>{
 const {dialog,mutations}=await fixture(page,baseURL!,true,true)
 await expect(dialog.getByRole('combobox',{name:'工序加工记录',exact:true})).toBeEnabled()
 await choose(page,dialog.getByRole('combobox',{name:'工序加工记录',exact:true}),'历史记录 / 融定 / 加工乙 / 7')
 await dialog.getByRole('button',{name:'保存草稿',exact:true}).click();await expect(dialog).not.toBeVisible()
 expect(mutations[0].body).toMatchObject({type:'SEMI_IN',executionId:'7',orderItemId:'31',orderStepId:'42',processorId:'82'})
})
