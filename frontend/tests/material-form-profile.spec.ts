import {test,expect,type Page,type Locator} from '@playwright/test'
import {csvText} from '../src/views/metal/storage-preview/csv'
async function select(page:Page,scope:Page|Locator,label:string,option:string){await scope.getByRole('combobox',{name:label,exact:true}).press('ArrowDown');await page.getByRole('option',{name:option,exact:true}).click();await page.keyboard.press('Escape')}
async function setup(page:Page,baseURL:string,roles=['仓管员','复验员','仓管员']){
 const materials=[{id:'1',code:'M1',name:'粉料样本',materialForm:'POWDER',measurementMode:'WEIGHT'},{id:'2',code:'M2',name:'未处理样本',materialForm:'UNTREATED',measurementMode:'WEIGHT'},{id:'3',code:'M3',name:'金属样本',materialForm:'METAL',measurementMode:'WEIGHT'},{id:'4',code:'M4',name:'缺失形态',measurementMode:'WEIGHT'}]
 const doc={id:'31',factoryId:'101',number:'RAW31',type:'RAW_IN',kind:'IN',status:'DRAFT',version:1,creatorId:'1',reviewerId:'9',warehouseIds:['11'],date:'2026-11-11',lines:[{id:'41',materialId:'1',warehouseId:'11',ownerId:'0',form:'METAL',netWeight:'10.00',measurementMode:'WEIGHT',name:'粉料样本',code:'M1'}]}
 await page.context().addCookies([{name:'Admin-Token',value:'mock-material-profile',url:baseURL}])
 await page.route('**/*-api/**',async route=>{const path=new URL(route.request().url()).pathname.replace(/^\/(dev|prod)-api/,'');let body:any={code:200,data:[],rows:[],total:0}
 if(path==='/getInfo')body={code:200,user:{userId:1,userName:'admin',nickName:'李二',roles:roles.map(roleName=>({roleName}))},roles:['admin'],permissions:['*:*:*']}
 if(path==='/metal/options/factories')body={code:200,data:[{id:'101',name:'本厂'}]}
 if(path==='/metal/storage-orders/list')body={code:200,rows:[doc],total:1}
 if(path==='/metal/storage-orders/31')body={code:200,data:doc}
 if(path==='/metal/storage-review-tasks/count')body={code:200,data:{count:0}}
 if(path==='/metal/storage-orders/options')body={code:200,data:{warehouses:[{id:'11',name:'原料库'}],materials,people:[{id:'9',name:'复验员',canAccept:true,canReceive:true}]}}
 await route.fulfill({json:body})})
 await page.goto('/metal/storage-orders');await select(page,page,'所属工厂','本厂');await page.getByRole('tab',{name:'入库单',exact:true}).click()
}
async function newReceipt(page:Page){await page.getByRole('button',{name:'添加',exact:true}).click();const dialog=page.getByRole('dialog',{name:'添加入库单',exact:true});await select(page,dialog,'仓库/库房','原料库');return dialog}
test('forms follow material selection and reject missing configuration',async({page,baseURL})=>{
 await setup(page,baseURL!);const dialog=await newReceipt(page);await dialog.getByRole('button',{name:'添加物料',exact:true}).click();const line=page.getByRole('dialog',{name:'物料明细',exact:true}),form=line.getByRole('textbox',{name:'物料形态',exact:true})
 await expect(form).toHaveValue('');await expect(form).toHaveAttribute('readonly','')
 for(const [code,name,value] of [['M1','粉料样本','粉料'],['M2','未处理样本','未处理料'],['M3','金属样本','金属料']]){await select(page,line,'入库物料',`【原料】 ${code} / ${name}`);await expect(form).toHaveValue(value)}
 await line.getByRole('heading',{name:'物料明细',exact:true}).click();await expect(page.getByRole('option',{name:'【原料】 M3 / 金属样本',exact:true})).not.toBeVisible();await page.screenshot({path:'../.runtime/material-form-profile/form.png'})
 await select(page,line,'入库物料','【原料】 M4 / 缺失形态');await expect(form).toHaveValue('');await line.getByRole('textbox',{name:'实测净重（g）',exact:true}).fill('1');await line.getByRole('button',{name:'确认',exact:true}).click();await expect(line).toContainText('该物料未配置有效物料形态')
 await select(page,line,'入库物料','【原料】 M1 / 粉料样本');await line.getByRole('button',{name:'确认',exact:true}).click();await expect(line).not.toBeVisible();await expect(dialog.getByRole('cell',{name:'M1 粉料样本',exact:true})).toBeVisible()
})
test('draft replaces stale form with configured form',async({page,baseURL})=>{
 await setup(page,baseURL!);await page.getByRole('row').filter({hasText:'RAW31'}).getByRole('button',{name:'编辑',exact:true}).click();const dialog=page.getByRole('dialog',{name:'编辑入库单',exact:true});await dialog.getByRole('row').filter({hasText:'粉料样本'}).getByRole('button',{name:'编辑',exact:true}).click();await expect(page.getByRole('dialog',{name:'物料明细',exact:true}).getByRole('textbox',{name:'物料形态',exact:true})).toHaveValue('粉料')
})
test('CSV rejects missing configuration and uses material form',async({page,baseURL})=>{
 await setup(page,baseURL!);const dialog=await newReceipt(page)
 const headers=['库存ID','物料ID','产品ID','原领料明细ID','料权客户ID(工厂填0)','净重(g)','数量(件)','参考完工件数','工厂料实测归还重量(g)','纯度(%)','形态(POWDER/UNTREATED/METAL)','外部来源凭证','已完成工序ID(分号分隔)','备注']
 const upload=async(id:string)=>dialog.locator('input[type=file]').setInputFiles({name:'test.csv',mimeType:'text/csv',buffer:Buffer.from(csvText(headers,[['',id,'','','0','1','','','','','METAL','','','']]))})
 await upload('4');await expect(dialog).toContainText('该物料未配置有效物料形态');await expect(dialog.getByRole('cell',{name:'M4 缺失形态',exact:true})).toHaveCount(0)
 await upload('1');const saved=page.waitForRequest(r=>r.method()==='POST'&&new URL(r.url()).pathname.endsWith('/metal/storage-orders'));await dialog.getByRole('button',{name:'保存草稿',exact:true}).click();expect((await saved).postDataJSON().lines[0].form).toBe('POWDER')
})
for(const roles of [[],['仓管员'],['仓管员','复验员','仓管员'],['很长的角色名称'.repeat(8),'复验员']])test(`profile roles ${roles.join(',')||'none'} fit desktop and mobile`,async({page,baseURL})=>{
 await setup(page,baseURL!,roles);const label=roles.length?`李二（${[...new Set(roles)].join('、')}）`:'李二',profile=page.locator('.profile-name');await expect(profile).toHaveText(label);await expect(profile).toHaveAttribute('title',label)
 for(const width of [1920,390]){await page.setViewportSize({width,height:900});await expect(page.getByRole('button',{name:'账户菜单',exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'待复验消息',exact:true})).toBeVisible();expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);if(roles[0]?.length>20)await page.screenshot({path:`../.runtime/material-form-profile/profile-${width}.png`})}
 roles.splice(0,roles.length,'切换后的角色');await page.evaluate(async()=>{const {default:useUserStore}=await import('/src/store/modules/user.ts');await useUserStore().getInfo()});await expect(profile).toHaveText('李二（切换后的角色）')
 // Exercise session cleanup without sending a logout request.
 await page.evaluate(async()=>{const {default:useUserStore}=await import('/src/store/modules/user.ts');useUserStore().clearSession()});await expect(profile).toHaveText('李二')
})
