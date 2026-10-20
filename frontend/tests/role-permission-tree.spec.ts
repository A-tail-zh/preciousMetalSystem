import {test,expect,type Page} from '@playwright/test'
async function setup(page:Page){
 const menus=[{id:'100',name:'父级目录',parentId:'0'},{id:'110',name:'子级菜单',parentId:'100'},{id:'111',name:'查看按钮',parentId:'110'},{id:'112',name:'编辑按钮',parentId:'110'},{id:'200',name:'其他目录',parentId:'0'},...Array.from({length:30},(_,i)=>({id:String(201+i),name:'其他权限'+i,parentId:'200'}))]
 const existing={id:'99',roleName:'已有角色',factoryId:'1',roleSort:0,status:'0',version:0,menuIds:['100','110','111']};const saves:any[]=[]
 await page.context().addCookies([{name:'Admin-Token',value:'mock-role-token',url:'http://127.0.0.1:5173'}]);
 await page.route('**/dev-api/**',async route=>{const req=route.request(),path=new URL(req.url()).pathname.replace('/dev-api','');let response:any={code:200,msg:'操作成功',data:[]};
 if(path==='/getInfo')response={code:200,user:{userId:1,userName:'admin',nickName:'管理员'},roles:['admin'],permissions:['*:*:*']};
 else if(path==='/getRouters')response.data=[{path:'/system',component:'Layout',name:'System',children:[{path:'role',component:'metal/role/index',name:'Role',meta:{title:'角色管理',noCache:true}}]}];
 else if(path==='/system/role/list')response={code:200,rows:[existing],total:1};
 else if(path==='/system/role/99')response.data=existing;
 else if(path==='/metal/options/factories')response.data=[{id:'1',name:'测试工厂'}];
 else if(path==='/metal/options/menus')response.data=menus;
 else if(path==='/system/role'&&['POST','PUT'].includes(req.method()))saves.push(req.postDataJSON());
 await route.fulfill({json:response});});await page.goto('/system/role');await expect(page.getByRole('cell',{name:'已有角色',exact:true})).toBeVisible();return {saves}
}
function entry(page:Page,name:string){return page.locator('.role-permission-tree .el-tree-node__content').filter({has:page.getByText(name,{exact:true})})}
test('parent selects descendants, independent branch stays clear and tree scrolls',async({page})=>{
 const {saves}=await setup(page);await page.getByRole('button',{name:'添加',exact:true}).click();const dialog=page.getByRole('dialog',{name:'添加角色',exact:true});
 await expect(dialog).toBeVisible();await dialog.locator('.el-form-item').filter({hasText:'角色名称'}).locator('input').fill('新增角色');
 await dialog.locator('.el-form-item').filter({hasText:'所属工厂'}).locator('.el-select__wrapper').click();await page.getByRole('option',{name:'测试工厂',exact:true}).click();
 const region=dialog.getByRole('region',{name:'菜单和按钮权限列表'});expect(await region.evaluate(e=>e.scrollHeight>e.clientHeight&&e.clientHeight<=360)).toBeTruthy();
 await entry(page,'父级目录').locator('.el-checkbox').click();for(const name of ['父级目录','子级菜单','查看按钮','编辑按钮'])await expect(entry(page,name).locator('input')).toBeChecked();await expect(entry(page,'其他目录').locator('input')).not.toBeChecked();
 await entry(page,'父级目录').locator('.el-checkbox').click();for(const name of ['子级菜单','查看按钮','编辑按钮'])await expect(entry(page,name).locator('input')).not.toBeChecked();
 await entry(page,'父级目录').locator('.el-checkbox').click();await entry(page,'编辑按钮').locator('.el-checkbox').click();
 await dialog.getByRole('button',{name:'确定',exact:true}).click();await expect(dialog).not.toBeVisible();expect(saves).toHaveLength(1);expect(saves[0].menuIds.sort()).toEqual(['100','110','111']);
})
test('existing parent grant does not expand permissions merely by opening edit',async({page})=>{
 const {saves}=await setup(page);await page.getByRole('button',{name:'编辑',exact:true}).last().click();const dialog=page.getByRole('dialog',{name:'编辑角色',exact:true});await expect(dialog).toBeVisible();
 await expect(entry(page,'父级目录').locator('input')).toBeChecked();await expect(entry(page,'查看按钮').locator('input')).toBeChecked();await expect(entry(page,'编辑按钮').locator('input')).not.toBeChecked();
 await dialog.getByRole('button',{name:'确定',exact:true}).click();await expect(dialog).not.toBeVisible();expect(saves[0].menuIds.sort()).toEqual(['100','110','111']);
})
test('cancelled permission selections are cleared when adding again',async({page})=>{
 await setup(page);await page.getByRole('button',{name:'添加',exact:true}).click();let dialog=page.getByRole('dialog',{name:'添加角色',exact:true});await entry(page,'父级目录').locator('.el-checkbox').click();await dialog.getByRole('button',{name:'取消',exact:true}).click();await expect(dialog).not.toBeVisible();
 await page.getByRole('button',{name:'添加',exact:true}).click();dialog=page.getByRole('dialog',{name:'添加角色',exact:true});await expect(entry(page,'父级目录').locator('input')).not.toBeChecked();await expect(entry(page,'查看按钮').locator('input')).not.toBeChecked();
 await page.screenshot({path:'../.runtime/role-permission-tree.png',fullPage:true,animations:'disabled'});
})

test('live role options contain application functions without scaffold tools',async({page,context})=>{
 const {login,api,ok}=await import('../../tools/local-test-client.mjs');const token=await login();try{
 const menus=ok(await api('/metal/options/menus',token)).data;
 expect(menus.some((m:any)=>m.perms==='metal:product:list')).toBeTruthy();expect(menus.some((m:any)=>m.perms==='metal:process-batch:settle')).toBeTruthy();expect(menus.some((m:any)=>m.perms==='system:role:add')).toBeTruthy();
 expect(menus.some((m:any)=>/^(tool:|system:(menu|dept|post|dict|config|notice):|monitor:(online|job|druid|server|cache):)/.test(m.perms??''))).toBeFalsy();
 const ids=new Set(menus.map((m:any)=>m.id));expect(menus.every((m:any)=>m.parentId==='0'||ids.has(m.parentId))).toBeTruthy();
 await context.addCookies([{name:'Admin-Token',value:token,url:'http://127.0.0.1:5173'}]);await page.goto('/system/role');await page.getByRole('button',{name:'添加',exact:true}).click();
 const dialog=page.getByRole('dialog',{name:'添加角色',exact:true});const region=dialog.getByRole('region',{name:'菜单和按钮权限列表'});await expect(region).toBeVisible();
 for(const text of ['代码生成','表单构建','系统接口','部门管理','岗位管理','定时任务'])await expect(region.getByText(text,{exact:true})).toHaveCount(0);
 await expect(region.getByText('产品管理',{exact:true})).toBeVisible();await page.screenshot({path:'../.runtime/role-business-permissions.png',fullPage:true,animations:'disabled'});await dialog.getByRole('button',{name:'取消',exact:true}).click();
 }finally{await api('/logout',token,'POST')}
})
