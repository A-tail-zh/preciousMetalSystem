import {test,expect,type Page,type Locator} from '@playwright/test'
import fs from 'node:fs/promises'
import {parseCsv,csvText} from '../src/views/metal/storage-preview/csv'
async function setup(page:Page,permissions=['*:*:*']) {
 const requests:{path:string,method:string}[]=[]
 await page.context().addCookies([{name:'Admin-Token',value:'storage-orders-test',url:String(test.info().project.use.baseURL)}])
 await page.route('**/dev-api/**',async route=>{const path=new URL(route.request().url()).pathname.replace('/dev-api','');requests.push({path,method:route.request().method()});await route.fulfill({json:path==='/getInfo'?{code:200,user:{userId:1,userName:'admin',nickName:'管理员'},roles:['admin'],permissions}:{code:200,data:[],rows:[],total:0}})})
 await page.goto('/metal/storage-orders');await select(page,page,'所属工厂','演示工厂一');return requests
}
async function select(page:Page,scope:Page|Locator,label:string,option:string|RegExp){await scope.getByRole('combobox',{name:label,exact:true}).press('ArrowDown');await page.getByRole('option',{name:option,exact:typeof option==='string'}).first().click()}
async function warehouse(page:Page,dialog:Locator,name='原料库'){await select(page,dialog,'仓库/库房',name);await dialog.getByRole('heading',{name:'物料清单',exact:true}).click()}
async function addLine(page:Page,dialog:Locator,{pieces=false,customer=false}={}){
 await dialog.getByRole('button',{name:'添加物料',exact:true}).click();const line=page.getByRole('dialog',{name:'物料明细',exact:true})
 await select(page,line,'库存物料',/M001|P001/);await line.getByRole('textbox',{name:'实测净重（g）',exact:true}).fill('10.25')
 if(pieces)await line.getByRole('textbox',{name:'数量（件）',exact:true}).fill('2')
 if(customer){await select(page,line,'材料归属','客户的料');await line.getByRole('button',{name:'添加客户',exact:true}).click();const c=page.getByRole('dialog',{name:'添加预览客户',exact:true});for(const [name,value]of [['客户名称','临时演示客户'],['客户公司名称','本地测试公司'],['客户地址','演示地址'],['联络人名称','测试联系人'],['联络人电话','13900000000']])await c.getByRole('textbox',{name:'新增'+name,exact:true}).fill(value);await c.getByRole('button',{name:'保存预览客户',exact:true}).click();await expect(line).toContainText('本地测试公司')}
 await line.getByRole('button',{name:'确认',exact:true}).click();await expect(line).not.toBeVisible()
}
async function production(page:Page,dialog:Locator,weight=false){await select(page,dialog,'生产工单',weight?'PREVIEW-GD-2':'PREVIEW-GD-1');await select(page,dialog,'产品明细',weight?/P002/:/P001/);await select(page,dialog,'工序',weight?'压制（按重量）':'压制（按件）')}
async function stock(page:Page){return page.evaluate(async()=>{const path='/src/views/metal/storage-preview/state.ts';return JSON.stringify((await import(path)).preview.inventory)})}
test('raw receipt creates preview customer and CSV export, with no business requests or stock changes',async({page})=>{
 const requests=await setup(page),before=await stock(page);await page.getByRole('tab',{name:'入库单',exact:true}).click();await page.getByRole('button',{name:'添加',exact:true}).click();const dialog=page.getByRole('dialog',{name:'添加入库工单',exact:true});await warehouse(page,dialog);await addLine(page,dialog,{customer:true});await dialog.getByRole('button',{name:'确认入库（预览）',exact:true}).click();await expect(dialog).not.toBeVisible();await expect(page.getByRole('cell',{name:'原材料入库',exact:true})).toBeVisible();await expect(page.getByRole('cell',{name:'已完成',exact:true})).toBeVisible();expect(await stock(page)).toBe(before)
 const downloaded=page.waitForEvent('download');await page.getByRole('button',{name:'导出',exact:true}).click();const download=await downloaded;expect(await fs.readFile((await download.path())!,'utf8')).toContain('PREVIEW-IN-');expect(requests.filter(r=>r.path.startsWith('/metal/')||r.method!=='GET')).toEqual([])
})
test('production issue confirmation requires variance remark and does not change stock',async({page})=>{
 await setup(page);const before=await stock(page);await page.getByRole('button',{name:'添加',exact:true}).click();const dialog=page.getByRole('dialog',{name:'添加出库工单',exact:true});await production(page,dialog);await warehouse(page,dialog);await select(page,dialog,'加工人员','演示加工人员');await addLine(page,dialog,{pieces:true});await dialog.getByRole('button',{name:'提交确认（预览）',exact:true}).click();await expect(dialog).not.toBeVisible();await page.getByRole('button',{name:'领料确认',exact:true}).click();const review=page.getByRole('dialog',{name:'领料确认（预览）',exact:true});await review.getByRole('textbox',{name:'实际净重（g）',exact:true}).fill('9.50');await review.getByRole('button',{name:'确认通过',exact:true}).click();await expect(review).toContainText('请填写差异备注');await review.getByRole('textbox',{name:'差异备注',exact:true}).fill('演示称重差异');await review.getByRole('button',{name:'确认通过',exact:true}).click();await expect(review).not.toBeVisible();expect(await stock(page)).toBe(before)
})
test('semi receipt hides weight quantity, supports return and resubmit then approval',async({page})=>{
 await setup(page);await page.getByRole('tab',{name:'入库单',exact:true}).click();await page.getByRole('button',{name:'添加',exact:true}).click();const dialog=page.getByRole('dialog',{name:'添加入库工单',exact:true});await select(page,dialog,'单据类型','半成品入库');await production(page,dialog,true);await warehouse(page,dialog,'成品库');await select(page,dialog,'仓管员','演示库管');await dialog.getByRole('button',{name:'添加物料',exact:true}).click();const line=page.getByRole('dialog',{name:'物料明细',exact:true});await select(page,line,'库存物料',/P002/);await line.getByRole('textbox',{name:'实测净重（g）',exact:true}).fill('5');await expect(line.getByRole('textbox',{name:'数量（件）',exact:true})).toHaveCount(0);await expect(line.getByRole('combobox',{name:'已完成步骤',exact:true})).toBeVisible();await line.getByRole('button',{name:'确认',exact:true}).click();await dialog.getByRole('button',{name:'提交确认（预览）',exact:true}).click();await page.getByRole('button',{name:'审核',exact:true}).click();const review=page.getByRole('dialog',{name:'库管审核（预览）',exact:true});await review.getByRole('button',{name:'退回',exact:true}).click();await expect(review).toContainText('请填写退回原因');await review.getByRole('textbox',{name:'审核说明',exact:true}).fill('请核对数量');await review.getByRole('button',{name:'退回',exact:true}).click();await page.getByRole('row').filter({hasText:'半成品入库'}).getByRole('button',{name:'编辑',exact:true}).click();await page.getByRole('dialog',{name:'编辑入库工单',exact:true}).getByRole('button',{name:'提交确认（预览）',exact:true}).click();await page.getByRole('button',{name:'审核',exact:true}).click();await review.getByRole('button',{name:'确认通过',exact:true}).click();await expect(review).not.toBeVisible();await expect(page.getByRole('cell',{name:'已完成',exact:true})).toBeVisible()
})
test('return references a completed issue and requires keeper approval',async({page})=>{
 await setup(page);await page.getByRole('tab',{name:'还库单',exact:true}).click();await page.getByRole('button',{name:'添加',exact:true}).click();const dialog=page.getByRole('dialog',{name:'添加还库工单',exact:true});await select(page,dialog,'对应出库工单',/PREVIEW-OUT-/);await warehouse(page,dialog);await select(page,dialog,'仓管员','演示库管');await addLine(page,dialog);await dialog.getByRole('button',{name:'提交确认（预览）',exact:true}).click();await expect(page.getByRole('cell',{name:'待库管审核',exact:true})).toBeVisible();await page.getByRole('button',{name:'审核',exact:true}).click();await page.getByRole('dialog',{name:'库管审核（预览）',exact:true}).getByRole('button',{name:'确认通过',exact:true}).click();await expect(page.getByRole('cell',{name:'已完成',exact:true})).toBeVisible()
})
test('CSV import validates whole batch and draft deletion confirms',async({page})=>{
 await setup(page);await page.getByRole('tab',{name:'入库单',exact:true}).click();await page.getByRole('button',{name:'添加',exact:true}).click();const dialog=page.getByRole('dialog',{name:'添加入库工单',exact:true});await warehouse(page,dialog)
 const downloading=page.waitForEvent('download');await dialog.getByRole('button',{name:'下载模板',exact:true}).click();const download=await downloading,headers=parseCsv(await fs.readFile((await download.path())!,'utf8'))[0]
 const values=(code:string)=>headers.map(h=>h.includes('编码')?code:h.includes('净重')?'10.00':h.includes('归属')?'工厂的料':'')
 const upload=(rows:string[][])=>dialog.locator('input[type=file]').setInputFiles({name:'sample.csv',mimeType:'text/csv',buffer:Buffer.from(csvText(headers,rows))})
 await upload([values('M001'),values('BAD')]);await expect(dialog).toContainText('第 3 行');await expect(dialog.getByRole('cell',{name:'M001',exact:true})).toHaveCount(0)
 await upload([values('M001')]);await expect(dialog.getByRole('cell',{name:'M001',exact:true})).toBeVisible();await dialog.getByRole('button',{name:'保存草稿',exact:true}).click();const row=page.getByRole('row').filter({hasText:'预览草稿'});await row.getByRole('button',{name:'删除',exact:true}).click();await page.getByRole('dialog',{name:'删除仓储工单',exact:true}).getByRole('button',{name:'删除',exact:true}).click();await expect(row).toHaveCount(0)
})
for(const type of ['产品出库','半成品出库']) test(type+' uses piece quantities and confirms without production fields',async({page})=>{
 await setup(page);await page.getByRole('button',{name:'添加',exact:true}).click();const dialog=page.getByRole('dialog',{name:'添加出库工单',exact:true});await select(page,dialog,'单据类型',type);await expect(dialog.getByRole('combobox',{name:'生产工单',exact:true})).toHaveCount(0);await warehouse(page,dialog,'成品库');await select(page,dialog,'加工人员','演示加工人员');await addLine(page,dialog,{pieces:true});await dialog.getByRole('button',{name:'提交确认（预览）',exact:true}).click();await expect(dialog).not.toBeVisible();await page.getByRole('button',{name:'领料确认',exact:true}).click();await page.getByRole('dialog',{name:'领料确认（预览）',exact:true}).getByRole('button',{name:'确认通过',exact:true}).click();await expect(page.getByRole('row').filter({hasText:type})).toContainText('已完成')
})
test('finished receipt keeps independent lines in multiple warehouses and no completed-steps field',async({page})=>{
 await setup(page);await page.getByRole('tab',{name:'入库单',exact:true}).click();await page.getByRole('button',{name:'添加',exact:true}).click();const dialog=page.getByRole('dialog',{name:'添加入库工单',exact:true});await select(page,dialog,'单据类型','成品入库');await production(page,dialog);await select(page,dialog,'仓管员','演示库管');await warehouse(page,dialog,'原料库');await addLine(page,dialog,{pieces:true});await warehouse(page,dialog,'成品库');await dialog.getByRole('tab',{name:'成品库',exact:true}).click();await dialog.getByRole('button',{name:'添加物料',exact:true}).click();const line=page.getByRole('dialog',{name:'物料明细',exact:true});await select(page,line,'库存物料',/P001/);await expect(line.getByRole('combobox',{name:'已完成步骤',exact:true})).toHaveCount(0);await line.getByRole('textbox',{name:'实测净重（g）',exact:true}).fill('2');await line.getByRole('textbox',{name:'数量（件）',exact:true}).fill('1');await line.getByRole('button',{name:'确认',exact:true}).click();await dialog.getByRole('button',{name:'提交确认（预览）',exact:true}).click();await expect(dialog).not.toBeVisible();await page.getByRole('button',{name:'审核',exact:true}).click();const review=page.getByRole('dialog',{name:'库管审核（预览）',exact:true});await expect(review.getByRole('textbox',{name:'实际净重（g）',exact:true})).toHaveCount(2);await review.getByRole('button',{name:'确认通过',exact:true}).click();await expect(page.getByRole('row').filter({hasText:'成品入库'})).toContainText('已完成')
})


test('pending messages open permitted reviews and layout follows requested order',async({page})=>{
 await page.setViewportSize({width:1920,height:1000});await setup(page)
 await expect(page.getByRole('combobox',{name:'演示操作身份',exact:true})).toHaveCount(0)
 const links=await page.locator('nav a[href*="/metal/storage-"]').allTextContents()
 expect(links).toEqual(['仓储管理','仓储工单管理','仓库管理'])
 const headers=await page.locator('.el-table__header-wrapper').first().locator('th').allTextContents()
 expect(headers.indexOf('生产工单')).toBe(headers.indexOf('单据编号')+1)
 expect(headers.indexOf('出库时间')).toBe(headers.indexOf('创建时间')-1)
 const dates=await page.getByPlaceholder('开始日期',{exact:true}).boundingBox()
 const factory=await page.getByRole('combobox',{name:'所属工厂',exact:true}).boundingBox()
 expect(Math.abs(dates!.y-factory!.y)).toBeLessThan(12)
 await page.getByRole('button',{name:'添加',exact:true}).click()
 const dialog=page.getByRole('dialog',{name:'添加出库工单',exact:true})
 await production(page,dialog);await warehouse(page,dialog);await addLine(page,dialog,{pieces:true})
 await dialog.getByRole('button',{name:'提交确认（预览）',exact:true}).click()
 await expect(dialog).not.toBeVisible()
 await page.getByRole('button',{name:/待复验消息\s*（1）/}).click()
 await page.locator('.review-message').click()
 const review=page.getByRole('dialog',{name:'领料确认（预览）',exact:true})
 await expect(review).toBeVisible();await review.getByRole('button',{name:'确认通过',exact:true}).click()
 await expect(page.getByRole('button',{name:'待复验消息',exact:true})).toBeVisible()
 await page.screenshot({path:'.runtime/storage-polish-20261109/orders.png',fullPage:true})
})


test('pending messages respect factory and receive versus accept permissions',async({page})=>{
 await setup(page,['metal:warehouse-order:list','metal:warehouse-order:receive'])
 await page.evaluate(async()=>{
  const path='/src/views/metal/storage-preview/state.ts';const {preview}=await import(path)
  const source=preview.orders.find((r:any)=>r.factoryId===preview.factoryId)
  preview.orders.push({...source,id:'own-receive',number:'本厂领料',status:'PENDING_RECEIPT'}, {...source,id:'own-accept',number:'本厂库管',status:'PENDING_KEEPER'}, {...source,id:'foreign',factoryId:'demo-b',number:'其他工厂',status:'PENDING_RECEIPT'})
 })
 await page.getByRole('button',{name:/待复验消息\s*（1）/}).click()
 await expect(page.locator('.review-message')).toHaveCount(1)
 await expect(page.locator('.review-message')).toContainText('本厂领料')
 await page.locator('.review-message').click()
 await expect(page.getByRole('dialog',{name:'领料确认（预览）',exact:true})).toBeVisible()
})
