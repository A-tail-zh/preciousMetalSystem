import {test,expect,type Page} from '@playwright/test'
async function setup(page:Page,authenticated=true,fault=false){
 const calls:{path:string;method:string;query:URLSearchParams;body:any}[]=[]
 if(authenticated)await page.context().addCookies([{name:'Admin-Token',value:'test-session-token',url:'http://127.0.0.1:5173'}]);
 await page.route('**/dev-api/**',async route=>{const req=route.request(),url=new URL(req.url()),path=url.pathname.replace('/dev-api','');let body:any;try{body=req.postDataJSON()}catch{body=req.postData()};calls.push({path,method:req.method(),query:url.searchParams,body});let response:any={code:200,data:[],rows:[],total:0};
 if(path==='/captchaImage')response={code:200,captchaEnabled:false};
 else if(path==='/login')response={code:500,msg:'账号或密码错误',traceId:'fictional-login-trace'};
 else if(path==='/getInfo')response={code:200,user:{userId:1,userName:'admin',nickName:'管理员'},roles:['admin'],permissions:['*:*:*']};
 else if(path==='/getRouters')response.data=[{path:'/metal',component:'Layout',children:['factory','product','production-line','material','process','quotation','warehouse-order'].map(p=>({path:p,component:'metal/'+p+'/index',name:p,meta:{title:p,noCache:true}}))},{path:'/system',component:'Layout',children:[{path:'user',component:'metal/staff/index',name:'User'},{path:'role',component:'metal/role/index',name:'Role'}]},{path:'/audit',component:'Layout',children:[{path:'operations',component:'audit/operations',name:'Operations'}]}];
 else if(path==='/metal/factory/list'&&fault)response={code:500,msg:'模拟业务错误',traceId:'fictional-business-trace'};
 else if(path.startsWith('/metal/options/'))response.data=path.endsWith('factories')?[{id:'1',name:'测试工厂'}]:[];
 else if(path.startsWith('/metal/filter-options/'))response={code:200,rows:[{id:'1',name:path.endsWith('roles')?'测试角色':'测试选项'}],hasMore:false};
 else if(path==='/metal/quotation/context')response.data={rule:{piValue:'3.141593'}};
 else if(path==='/metal/quotation/factories')response.data=[{id:'1',name:'测试工厂'}];
 else if(path==='/metal/quotation/calculate')response.data={volume:'15.000000',unitPrice:'10.123456',totalPrice:body.quantity===2?'20.246912':'30.370368'};
 else if(path.endsWith('/export')){await route.fulfill({contentType:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',body:Buffer.from('PK UI download mock')});return;}
 await route.fulfill({json:response});});return calls
}
test('login error has no fault trace and password can be revealed',async({page})=>{
 await setup(page,false);await page.goto('/login');await page.getByPlaceholder('账号').fill('fictional');const password=page.getByPlaceholder('密码',{exact:true});await password.fill('fictional-password');await expect(password).toHaveAttribute('type','password');await page.locator('.el-input__password').click();await expect(password).toHaveAttribute('type','text');await page.getByRole('button',{name:/登.*录/}).click();await expect(page.getByText('账号或密码错误',{exact:true})).toBeVisible();await expect(page.getByText('故障追踪',{exact:true})).toHaveCount(0);
})
test('authenticated business errors retain trace notification',async({page})=>{await setup(page,true,true);await page.goto('/metal/factory');await expect(page.getByText('故障追踪',{exact:true})).toBeVisible()})
test('staff default password resets and avatar menu opens on click',async({page})=>{
 await setup(page);await page.goto('/system/user');await page.getByRole('button',{name:'添加',exact:true}).click();let dialog=page.getByRole('dialog',{name:'添加人员',exact:true});const password=dialog.locator('input[type=password]');await expect(password).toHaveValue('12345678');await password.fill('changed-value');await dialog.getByRole('button',{name:'取消',exact:true}).click();await expect(dialog).not.toBeVisible();await page.getByRole('button',{name:'添加',exact:true}).click();dialog=page.getByRole('dialog',{name:'添加人员',exact:true});await expect(dialog.locator('input[type=password]')).toHaveValue('12345678');await dialog.getByRole('button',{name:'取消',exact:true}).click();await page.getByRole('button',{name:'账户菜单',exact:true}).click();await expect(page.getByText('退出登录',{exact:true})).toBeVisible();
})
test('all eight filter panels expose expected fields and preserve toolbar order',async({page})=>{
 await setup(page);
 for(const [path,labels]of [['/metal/factory',['工厂名称','详细地址']],['/metal/product',['产品编号','产品名称','产品类别']],['/metal/production-line',['产线编号','产线名称','所属工厂','关联产品']],['/metal/material',['物料编码','物料类型','规格型号']],['/metal/process',['工序名称','工序类型']],['/system/role',['角色名称','所属工厂']],['/system/user',['角色','所属工厂']],['/metal/warehouse-order',['单据编号','生产工单号','仓库','类型','经办人','领用人','仓管人']]] as [string,string[]][]){
 await page.goto(path);await page.getByRole('button',{name:'筛选条件',exact:true}).click();const panel=page.locator('.business-filter-panel:visible');await expect(panel).toBeVisible();for(const label of labels)await expect(panel.locator('.el-form-item__label').filter({hasText:new RegExp('^'+label+'$')})).toHaveCount(1);await panel.getByRole('button',{name:'收起',exact:true}).click();
 const toolbar=page.locator('.master-toolbar,.factory-toolbar,.toolbar').first();const texts=await toolbar.getByRole('button').allTextContents();expect(texts.slice(0,4)).toEqual(['添加','编辑','删除','筛选条件']);if(['/system/user','/metal/warehouse-order'].includes(path))expect(texts[4]).toBe('导出');
 }
})
test('role query and reset send filters before pagination',async({page})=>{
 const calls=await setup(page);await page.goto('/system/role');await page.getByRole('button',{name:'筛选条件',exact:true}).click();let panel=page.locator('.business-filter-panel:visible');await panel.locator('.el-form-item').filter({hasText:'角色名称'}).locator('input').fill('审核');await panel.getByRole('button',{name:'查询',exact:true}).click();await expect.poll(()=>calls.filter(c=>c.path==='/system/role/list').at(-1)?.query.get('name')).toBe('审核');expect(calls.filter(c=>c.path==='/system/role/list').at(-1)?.query.get('pageNum')).toBe('1');await page.getByRole('button',{name:/筛选条件/}).click();panel=page.locator('.business-filter-panel:visible');await panel.getByRole('button',{name:'重置',exact:true}).click();await expect.poll(()=>calls.filter(c=>c.path==='/system/role/list').at(-1)?.query.has('name')).toBe(false);
})
test('staff export includes applied role filter and blocks unqueried edits',async({page})=>{
 const calls=await setup(page);await page.goto('/system/user');await page.getByRole('button',{name:'筛选条件',exact:true}).click();const panel=page.locator('.business-filter-panel:visible');await panel.locator('.el-form-item').filter({has:page.locator('label').filter({hasText:/^角色$/})}).locator('.el-select__wrapper').click();await page.getByRole('option',{name:'测试角色',exact:true}).click();await panel.getByRole('button',{name:'收起',exact:true}).click();await page.getByRole('button',{name:'导出',exact:true}).click();await expect(page.getByText('筛选条件已修改，请先点击查询',{exact:true})).toBeVisible();expect(calls.some(c=>c.path==='/system/user/export')).toBe(false);
 await page.getByRole('button',{name:'筛选条件',exact:true}).click();await panel.getByRole('button',{name:'查询',exact:true}).click();const [download]=await Promise.all([page.waitForEvent('download'),page.getByRole('button',{name:'导出',exact:true}).click()]);expect(download.suggestedFilename()).toBe('人员.xlsx');const exported=calls.filter(c=>c.path==='/system/user/export').at(-1)?.body;expect(typeof exported==='string'?new URLSearchParams(exported).get('roleId'):exported?.roleId).toBe('1');
})
test('quotation calculation shows ordered result table and exact total',async({page})=>{
 await setup(page);await page.goto('/metal/quotation');
 const fill=async(index:number,quantity:string)=>{const item=page.locator('.goods-item').nth(index);await item.locator('.el-select__wrapper').click();await item.getByRole('option',{name:'铱杆实心',exact:true}).click();await item.getByLabel('商品数量',{exact:true}).fill(quantity);await item.getByLabel('直径：',{exact:true}).fill('2');await item.getByLabel('长度：',{exact:true}).fill('3')};
 await fill(0,'2');await page.getByRole('button',{name:'添加商品',exact:true}).click();await fill(1,'3');await page.getByRole('button',{name:'开始计算',exact:true}).click();const table=page.locator('.quotation-results-table');await expect(table.locator('.el-table__body .el-table__row')).toHaveCount(2);await expect(table).toContainText('20.246912');await expect(table).toContainText('30.370368');await expect(page.getByText('总报价：50.617280 元',{exact:true})).toBeVisible();await page.locator('.goods-item').first().getByLabel('长度：',{exact:true}).fill('4');await expect(table).toHaveCount(0);
})
test('log toolbar keeps filter query and export together',async({page})=>{await setup(page);await page.goto('/audit/operations');await expect(page.locator('.log-toolbar').getByRole('button')).toHaveText(['筛选条件','查询','导出']);await page.getByRole('button',{name:'筛选条件',exact:true}).click();await expect(page.getByPlaceholder('追踪ID')).toBeVisible()})
