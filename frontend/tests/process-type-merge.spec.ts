import {test,expect,type Page} from '@playwright/test'
const api=(path:string)=>path.replace(/^\/(?:dev|prod)-api/,'')
const types=[{id:'12',name:'酸洗',builtinCode:'ACID_WASH',semanticCode:'ACID_WASH',version:0,factoryId:'7',factoryName:'本厂'},{id:'13',name:'融定',builtinCode:'SMELTING',semanticCode:'SMELTING',version:0,factoryId:'7',factoryName:'本厂'},{id:'14',name:'焊接',builtinCode:null,semanticCode:'NORMAL',version:0,factoryId:'7',factoryName:'本厂'}]
async function setup(page:Page){
 const writes:any[]=[];
 await page.context().addCookies([{name:'Admin-Token',value:'merge-test',url:String(test.info().project.use.baseURL||'http://127.0.0.1:5173')}]);
 await page.route('**/{dev-api,prod-api}/**',async route=>{const req=route.request(),p=api(new URL(req.url()).pathname);let data:any={code:200,data:[],rows:[],total:0};
  if(p==='/getInfo')data={code:200,user:{userId:2,userName:'tester',factoryId:'7'},roles:['staff'],permissions:['metal:process:list','metal:process:add','metal:process:edit','metal:process-type:list','metal:process-type:add','metal:process-type:edit','metal:process-type:remove']};
  else if(p==='/getRouters')data.data=[{path:'/metal',component:'Layout',children:[{path:'process',component:'metal/process/index'}]}];
  else if(p.endsWith('/factories'))data.data=[{id:'7',name:'本厂'}];
  else if(p==='/metal/process/list'){data.rows=[];data.total=0}
  else if(p==='/metal/process/types'){
   if(req.method()==='GET')data.data=types
   else if(req.method()==='POST'){const body=req.postDataJSON();if(['酸洗','融定'].includes(body.name?.trim()))data={code:400,msg:'酸洗、融定为系统内置类型，请直接选用'};else{writes.push(body);data.data={id:'50',name:body.name,version:0}}}
  }
  else if(p==='/metal/process'&&req.method()==='POST'){writes.push(req.postDataJSON());data.data={id:'100'};}
  await route.fulfill({json:data});
 });await page.goto('/metal/process');await expect(page.locator('.master-table')).toBeVisible();return writes;
}
test('process selects only a type and never submits a separate semantic',async({page})=>{
 const writes=await setup(page);await page.getByRole('button',{name:'添加',exact:true}).click();const dialog=page.getByRole('dialog',{name:'添加工序',exact:true});
 await expect(dialog).toBeVisible();await expect(dialog.getByText('工艺类别',{exact:true})).toHaveCount(0);
 await dialog.getByRole('textbox').first().fill('精洗');
 await dialog.locator('.el-form-item').filter({hasText:'工序类型'}).getByRole('combobox').press('ArrowDown');await page.getByRole('option',{name:'酸洗',exact:false}).click();
 await dialog.getByRole('button',{name:'确定',exact:true}).click();await expect.poll(()=>writes.length).toBe(1);
 expect(writes[0].processType).toBe(12);expect(writes[0].storageConfiguration?.semanticCode).toBeUndefined();
});
test('builtin types are read only while custom types remain editable in process management',async({page})=>{
 await setup(page);
 await page.getByRole('button',{name:'维护工序类型',exact:true}).click();
 const dialog=page.getByRole('dialog',{name:'维护工序类型'});
 const acid=dialog.getByRole('row').filter({has:page.getByRole('cell',{name:/酸洗/})}).first();
 await expect(acid.getByText('不可改删')).toBeVisible();
 await expect(acid.getByRole('button',{name:'编辑',exact:true})).toHaveCount(0);
 await expect(acid.getByRole('button',{name:'删除',exact:true})).toHaveCount(0);
 await expect(dialog.getByRole('row').filter({hasText:'焊接'}).getByRole('button',{name:'编辑',exact:true})).toBeVisible();
 await dialog.getByPlaceholder('请输入工序类型名称').fill('酸洗');
 await dialog.getByRole('button',{name:'添加',exact:true}).click();
 await expect(page.getByText('酸洗、融定为系统内置类型，请直接选用')).toBeVisible();
});
