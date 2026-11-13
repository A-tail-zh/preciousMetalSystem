import {test,expect} from '@playwright/test'
import {readFile} from 'node:fs/promises'
import {rawInventoryGroups,inventoryOwners} from '../src/views/metal/storage-inventory/groups'
const stock=(id:string,extra:any={})=>({id,factoryId:'101',warehouseId:'11',category:'RAW',identityKey:'same-material',measurementMode:'WEIGHT',netWeight:'0.01',quantity:0,batchNo:`ST-${id}`,identity:{materialId:'5',code:'M001',name:'原料甲',form:'POWDER',specification:'标准',metalType:'金',purity:'99.00',unit:'g'},...extra})
test('group identity stays separate and sums exact weight and owner balances',()=>{
 const rows=[stock('1'),stock('2'),stock('3',{warehouseId:'12'}),stock('4',{identityKey:'purity-other'}),stock('5',{identityKey:''}),stock('6',{identityKey:''}),stock('7',{measurementMode:'PIECE'}),stock('8',{factoryId:'102'})]
 const result=rawInventoryGroups(rows)
 expect(result).toHaveLength(7);expect(result[0].netWeight).toBe('0.02');expect(result[0].sourceCount).toBe(2)
 const owners=inventoryOwners([{ownerships:[{ownerId:'1',companyName:'同名公司',weight:'0.01',quantity:1},{ownerId:'2',companyName:'同名公司',weight:'2.00',quantity:2}]},{ownerships:[{ownerId:'1',weight:'0.02',quantity:3}]}])
 expect(owners).toHaveLength(2);expect(owners[0].weight).toBe('0.03');expect(owners[0].quantity).toBe('4')
})
test('raw groups merge across API pages, detail shows owners and source adjustments retain original stock',async({page})=>{
 const base=String(test.info().project.use.baseURL),all=Array.from({length:501},(_,i)=>stock(String(i+1))),seen:string[]=[]
 await page.context().addCookies([{name:'Admin-Token',value:'inventory-group-test',url:base}])
 await page.route('**/*-api/**',async route=>{
  const url=new URL(route.request().url()),p=url.pathname.replace(/^\/(dev|prod)-api/,'');seen.push(p+url.search)
  let body:any={code:200,data:[],rows:[],total:0}
  if(p==='/getInfo')body={code:200,user:{userId:9,userName:'inventory',factoryId:'101'},permissions:['metal:inventory:list','metal:inventory:query','metal:inventory:adjust','metal:inventory:export'],roles:['staff']}
  if(p==='/metal/options/factories')body={code:200,data:[{id:'101',name:'工厂甲'}]}
  if(p==='/metal/storage-warehouses/options')body={code:200,data:[{id:'11',name:'原料库'}]}
  if(p==='/metal/storage-inventory/list'){const n=Number(url.searchParams.get('pageNum')),size=Number(url.searchParams.get('pageSize'));body={code:200,rows:all.slice((n-1)*size,n*size),total:all.length}}
  if(p==='/metal/storage-inventory/export')body={code:200,data:all}
  const match=p.match(/^\/metal\/storage-inventory\/(\d+)$/)
  if(match)body={code:200,data:{...all[Number(match[1])-1],ownerships:[{ownerId:'21',companyName:'公司甲',ownerName:'客户甲',weight:'0.01',quantity:0}],stockSlices:[]}}
  if(p==='/metal/storage-orders/options')body={code:200,data:{warehouses:[{id:'11',name:'原料库'}],stocks:all,customers:[],people:[]}}
  await route.fulfill({json:body})
 })
 await page.goto('/metal/storage-inventory')
 const main=page.locator('.storage-inventory-page > .el-table')
 await expect(main.getByRole('cell',{name:'M001',exact:true})).toBeVisible();await expect(main.getByRole('cell',{name:'5.01',exact:true})).toBeVisible();await expect(main.locator('tbody tr')).toHaveCount(1)
 expect(seen.some(x=>x.includes('pageNum=2')&&x.includes('pageSize=500'))).toBe(true)
 const downloading=page.waitForEvent('download');await page.getByRole('button',{name:'导出',exact:true}).click();const csv=await readFile((await (await downloading).path())!,'utf8');expect(csv.match(/M001/g)).toHaveLength(1);expect(csv).toContain('5.01')
 const adjustment=page.getByRole('dialog',{name:'添加库存调整单',exact:true})
 await main.getByRole('button',{name:'库存调整',exact:true}).click()
 await expect(adjustment).toBeVisible()
 await expect(page.getByRole('dialog',{name:'原材料汇总详情',exact:true})).toBeHidden()
 await expect(adjustment.getByRole('tab',{name:'原料库',exact:true})).toBeVisible()
 await expect(adjustment.getByRole('cell',{name:'ST-1',exact:true})).toHaveCount(0)
 await adjustment.getByRole('button',{name:'取消',exact:true}).click()
 await expect(adjustment).toBeHidden()
 await main.getByRole('button',{name:'查看详情',exact:true}).click()
 const detail=page.getByRole('dialog',{name:'原材料汇总详情',exact:true});await expect(detail).toBeVisible();await expect(detail.locator('.group-owners')).toContainText('公司甲');await expect(detail.locator('.group-owners')).toContainText('5.01');await expect(detail.locator('.group-sources tbody tr')).toHaveCount(501)
 expect(seen.some(x=>/\/ledger/.test(x))).toBe(false)
 await detail.locator('.group-sources').getByRole('button',{name:'库存调整',exact:true}).nth(1).click()
 const editor=page.getByRole('dialog',{name:'添加库存调整单',exact:true});await expect(editor).toBeVisible();await expect(editor.getByRole('cell',{name:'ST-2',exact:true})).toBeVisible()
 await page.screenshot({path:test.info().outputPath('inventory-groups.png'),fullPage:true})
})

test('a changing total never presents a partial raw summary',async({page})=>{
 await page.context().addCookies([{name:'Admin-Token',value:'inventory-incomplete-test',url:String(test.info().project.use.baseURL)}])
 await page.route('**/*-api/**',async route=>{
  const url=new URL(route.request().url()),p=url.pathname.replace(/^\/(dev|prod)-api/,'');let body:any={code:200,data:[],rows:[],total:0}
  if(p==='/getInfo')body={code:200,user:{userId:9,userName:'inventory',factoryId:'101'},permissions:['metal:inventory:list'],roles:['staff']}
  if(p==='/metal/options/factories')body={code:200,data:[{id:'101',name:'工厂甲'}]}
  if(p==='/metal/storage-inventory/list')body=url.searchParams.get('pageNum')==='1'?{code:200,rows:Array.from({length:500},(_,i)=>stock(String(i+1))),total:501}:{code:200,rows:[],total:500}
  await route.fulfill({json:body})
 })
 await page.goto('/metal/storage-inventory');await expect(page.getByText('库存发生变化，请重新查询',{exact:true})).toBeVisible();await expect(page.locator('.storage-inventory-page > .el-table tbody tr').filter({hasText:'M001'})).toHaveCount(0)
})
test('a late raw response cannot overwrite the selected product tab',async({page})=>{
 let release:()=>void=()=>{},started:()=>void=()=>{};const pending=new Promise<void>(resolve=>{release=resolve}),rawStarted=new Promise<void>(resolve=>{started=resolve})
 await page.context().addCookies([{name:'Admin-Token',value:'inventory-late-test',url:String(test.info().project.use.baseURL)}])
 await page.route('**/*-api/**',async route=>{
  const url=new URL(route.request().url()),p=url.pathname.replace(/^\/(dev|prod)-api/,'');let body:any={code:200,data:[],rows:[],total:0}
  if(p==='/getInfo')body={code:200,user:{userId:9,userName:'inventory',factoryId:'101'},permissions:['metal:inventory:list'],roles:['staff']}
  if(p==='/metal/options/factories')body={code:200,data:[{id:'101',name:'工厂甲'}]}
  if(p==='/metal/storage-inventory/list'){if(url.searchParams.get('category')==='RAW'){started();await pending;body={code:200,rows:[stock('1')],total:1}}else body={code:200,rows:[stock('2',{code:'P001',name:'成品甲',category:'PRODUCT'})],total:1}}
  await route.fulfill({json:body})
 })
 await page.goto('/metal/storage-inventory');await rawStarted;await page.getByRole('tab',{name:'成品',exact:true}).click();await expect(page.getByRole('cell',{name:'P001',exact:true})).toBeVisible();release();await page.waitForTimeout(100);await expect(page.getByRole('cell',{name:'P001',exact:true})).toBeVisible();await expect(page.getByRole('cell',{name:'M001',exact:true})).toHaveCount(0)
})
