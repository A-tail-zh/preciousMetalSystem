import {test,expect,type Page} from '@playwright/test'
const deferred=()=>{let resolve!:()=>void;const promise=new Promise<void>(r=>resolve=r);return{promise,resolve}}
async function setup(page:Page,admin=true){
 const state={lists:[] as URL[],details:[] as URL[],images:[] as URL[],downloads:[] as URL[],listGate:undefined as ReturnType<typeof deferred>|undefined,detailGate:undefined as ReturnType<typeof deferred>|undefined,downloadGate:undefined as ReturnType<typeof deferred>|undefined};
 await page.context().addCookies([{name:'Admin-Token',value:'quote-factory',url:String(test.info().project.use.baseURL)}]);
 await page.route('**/dev-api/**',async route=>{const u=new URL(route.request().url()),p=u.pathname.replace('/dev-api','');let r:any={code:200,data:[],rows:[],total:0};const factory=u.searchParams.get('factoryId');
 if(p==='/getInfo')r={code:200,user:{userId:admin?1:2,userName:'test'},roles:[admin?'admin':'staff'],permissions:['metal:quotation:list']};
 else if(p==='/getRouters')r.data=[{path:'/metal',component:'Layout',children:[{path:'quotation',component:'metal/quotation/index'}]}];
 else if(p==='/metal/quotation/factories')r.data=admin?[{id:'11',name:'工厂A'},{id:'22',name:'工厂B'}]:[{id:'11',name:'工厂A'}];
 else if(p==='/metal/quotation/context')r.data={metalTypes:[],productTypes:[],rules:[],volumeRule:{version:0}};
 else if(p==='/metal/quotation/orders'){state.lists.push(u);if(state.listGate)await state.listGate.promise;r.rows=[{id:factory,quoteNo:'QT-'+factory,factoryId:factory,factoryName:'厂'+factory,totalPrice:'10.00',items:[]}];r.total=1}
 else if(p.endsWith('/download')){state.downloads.push(u);if(state.downloadGate)await state.downloadGate.promise;await route.fulfill({contentType:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',body:'mock excel'});return}
 else if(p.endsWith('/image')){state.images.push(u);await route.fulfill({contentType:'image/png',body:Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==','base64')});return}
 else if(p.startsWith('/metal/quotation/orders/')){state.details.push(u);if(state.detailGate)await state.detailGate.promise;r.data={id:p.split('/').at(-1),quoteNo:'DETAIL-'+factory,factoryId:factory,factoryName:'厂'+factory,totalPrice:'10.00',items:[{category:'环',quantity:1,dimensions:{},dimensionFields:[]}]}}
 await route.fulfill({json:r})});await page.goto('/metal/quotation');return state
}
async function factory(page:Page,name:string){await page.getByRole('combobox',{name:'所属工厂',exact:true}).press('ArrowDown');await page.getByRole('option',{name,exact:true}).click()}
test('history shares top factory even for read-only admin, clears filters/detail and binds downloads/images',async({page})=>{
 const s=await setup(page);await expect(page.getByLabel('历史报价单号')).toBeDisabled();await expect(page.getByRole('combobox',{name:'历史工厂'})).toHaveCount(0);expect(s.lists).toHaveLength(0);
 await factory(page,'工厂A');await expect(page.getByRole('cell',{name:'QT-11',exact:true})).toBeVisible();await page.getByLabel('历史报价单号').fill('QT');await page.getByRole('button',{name:'查询',exact:true}).click();await expect.poll(()=>s.lists.at(-1)?.searchParams.get('quoteNo')).toBe('QT');
 await page.getByRole('button',{name:'查看',exact:true}).click();await expect(page.getByRole('dialog',{name:'历史报价详情'})).toBeVisible();await expect.poll(()=>s.images.length).toBe(1);expect(s.images[0].searchParams.get('factoryId')).toBe('11');expect(s.details[0].searchParams.get('factoryId')).toBe('11');
 await page.getByRole('dialog').getByRole('button',{name:'关闭此对话框'}).click();
 const downloaded=page.waitForEvent('download');await page.getByRole('button',{name:'下载',exact:true}).click();await downloaded;expect(s.downloads[0].searchParams.get('factoryId')).toBe('11');
 await factory(page,'工厂B');await expect(page.getByLabel('历史报价单号')).toHaveValue('');await expect(page.getByRole('cell',{name:'QT-22',exact:true})).toBeVisible();expect(s.lists.at(-1)?.searchParams.get('pageNum')).toBe('1');await expect(page.getByText('DETAIL-11',{exact:false})).toHaveCount(0);
})
test('late history/detail/download responses cannot return after factory switch',async({page})=>{
 const s=await setup(page);s.listGate=deferred();await factory(page,'工厂A');await expect.poll(()=>s.lists.length).toBe(1);const old=s.listGate;s.listGate=undefined;await factory(page,'工厂B');await expect(page.getByRole('cell',{name:'QT-22',exact:true})).toBeVisible();old.resolve();await page.waitForTimeout(200);await expect(page.getByRole('cell',{name:'QT-11',exact:true})).toHaveCount(0);
 s.detailGate=deferred();await page.getByRole('button',{name:'查看',exact:true}).click();await expect.poll(()=>s.details.length).toBe(1);const detail=s.detailGate;await factory(page,'工厂A');detail.resolve();await page.waitForTimeout(200);await expect(page.getByRole('dialog',{name:'历史报价详情'})).toHaveCount(0);
 await expect(page.getByRole('cell',{name:'QT-11',exact:true})).toBeVisible();s.downloadGate=deferred();let downloads=0;page.on('download',()=>downloads++);await page.getByRole('button',{name:'下载',exact:true}).click();await expect.poll(()=>s.downloads.length).toBe(1);await factory(page,'工厂B');s.downloadGate.resolve();await page.waitForTimeout(300);expect(downloads).toBe(0);
})
test('ordinary user loads history only for fixed own factory',async({page})=>{
 const s=await setup(page,false);await expect(page.getByRole('cell',{name:'QT-11',exact:true})).toBeVisible();await expect(page.getByRole('combobox',{name:'所属工厂'})).toHaveCount(0);expect(s.lists.every(u=>u.searchParams.get('factoryId')==='11')).toBe(true);
})
