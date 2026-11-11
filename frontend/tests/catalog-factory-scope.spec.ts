import {test,expect,type Page} from '@playwright/test'
const modules=[['factory','工厂名称','factory'],['product','产品编号','product'],['production-line','产线编号','production-line'],['process','工序名称','process'],['customer','客户名称','customer']] as const
async function setup(page:Page,path:string,permission:string,admin:boolean){
 const lists:string[]=[];await page.context().addCookies([{name:'Admin-Token',value:'catalog-isolation',url:String(test.info().project.use.baseURL)}]);
 await page.route('**/dev-api/**',async route=>{const u=new URL(route.request().url()),p=u.pathname.replace('/dev-api','');let r:any={code:200,data:[],rows:[],total:0};
 if(p==='/getInfo')r={code:200,user:{userId:admin?1:2,userName:'test'},roles:[admin?'admin':'staff'],permissions:[`metal:${permission}:list`,`metal:${permission}:add`,...(permission==='process'?['metal:process-type:add']:[])]};
 else if(p==='/getRouters')r.data=[{path:'/metal',component:'Layout',children:[{path,component:`metal/${path}/index`}]}];
 else if(p.endsWith('/factories')){if(admin)expect(u.searchParams.has('factoryId')).toBe(false);r.data=r.rows=admin?[{id:'7',name:'甲厂'},{id:'8',name:'乙厂'}]:[{id:'7',name:'甲厂'}]}
 else if(p.endsWith('/list'))lists.push(u.search);
 await route.fulfill({json:r})});await page.goto('/metal/'+path);return lists
}
for(const [path,label,permission]of modules){
 test(path+' admin factory scope behavior',async({page})=>{
  const lists=await setup(page,path,permission,true);const field=page.getByLabel(label,{exact:true});
  if(path==='factory'){
   await expect(page.getByRole('combobox',{name:'所属工厂',exact:true})).toHaveCount(0);await expect(field).toBeEnabled();await expect.poll(()=>lists.length).toBeGreaterThan(0);expect(lists.every(q=>!q.includes('factoryId'))).toBe(true);
   await field.fill('甲厂');await page.getByRole('button',{name:'查询',exact:true}).click();await expect.poll(()=>lists.at(-1)).toContain('name=');await page.getByRole('button',{name:'重置',exact:true}).click();await expect(field).toHaveValue('');await expect(page.getByRole('button',{name:'查询',exact:true})).toBeEnabled();await expect.poll(()=>lists.at(-1)).not.toContain('name=');return
  }
  await expect(field).toBeDisabled();await expect(page.getByRole('button',{name:'查询',exact:true})).toBeDisabled();expect(lists).toHaveLength(0);
  await expect(page.getByText(/请先选择所属工厂，再/)).toHaveCount(0);
  await page.waitForTimeout(5500);await expect(page.getByText(/列表更新失败/)).toHaveCount(0);expect(lists).toHaveLength(0);
  if(path==='process')await expect(page.getByRole('button',{name:'添加工序类型',exact:true})).toBeDisabled();
  if(path==='factory')await expect(page.getByRole('button',{name:'添加',exact:true})).toBeEnabled();else await expect(page.getByRole('button',{name:'添加',exact:true})).toBeDisabled();
  await page.getByRole('combobox',{name:'所属工厂',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'甲厂',exact:true}).click();await expect.poll(()=>lists.at(-1)).toContain('factoryId=7');await expect(page.getByText(/列表更新失败/)).toHaveCount(0);await field.fill('旧条件');
  await page.getByRole('combobox',{name:'所属工厂',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'乙厂',exact:true}).click();await expect.poll(()=>lists.at(-1)).toContain('factoryId=8');await expect(field).toHaveValue('');expect(lists.at(-1)).not.toContain(encodeURIComponent('旧条件'));
  await page.getByRole('button',{name:'重置',exact:true}).click();await expect(field).toBeDisabled();await page.waitForTimeout(5500);await expect(page.getByText(/列表更新失败/)).toHaveCount(0)
 })
 test(path+' ordinary user remains in own factory after reset',async({page})=>{
  const lists=await setup(page,path,permission,false);await expect.poll(()=>lists.length).toBeGreaterThan(0);expect(lists.every(q=>q.includes('factoryId=7'))).toBe(true);const factory=page.getByRole('combobox',{name:'所属工厂',exact:true});await expect(factory).toHaveCount(0);
  await page.getByRole('button',{name:'重置',exact:true}).click();await expect(page.getByRole('button',{name:'查询',exact:true})).toBeEnabled();expect(lists.every(q=>q.includes('factoryId=7'))).toBe(true)
 })
}

test('factory real list failure remains visible and retry works',async({page})=>{
 await setup(page,'factory','factory',true);
 await page.route('**/dev-api/metal/factory/list?**',route=>route.fulfill({status:500,json:{code:500,msg:'test failure'}}));
 await page.getByRole('button',{name:'查询',exact:true}).click();
 await expect(page.getByText('工厂列表更新失败，当前显示可能过期')).toBeVisible();
 await page.unroute('**/dev-api/metal/factory/list?**');await page.getByRole('button',{name:'重试',exact:true}).click();
 await expect(page.getByText('工厂列表更新失败，当前显示可能过期')).toHaveCount(0);
})
