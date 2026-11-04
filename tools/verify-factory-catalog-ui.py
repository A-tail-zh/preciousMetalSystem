"""Real browser checks against the explicitly registered factory isolation fixture."""
from pathlib import Path
import json,re,os
from playwright.sync_api import sync_playwright,expect

root=Path(__file__).resolve().parents[1]/'.runtime/factory-scope-20261104'
manifest=json.loads((root/'last-live.json').read_text(encoding='utf-8'))
folder=root/('live-'+manifest['prefix'].removeprefix('工单验收_'))
fixture=json.loads((folder/'browser.private.json').read_text(encoding='utf-8'))
admin=json.loads((root/'browser-admin.private.json').read_text(encoding='utf-8'))['token']
base='http://127.0.0.1:5197';assert fixture['environment']['api']=='http://127.0.0.1:8093'
artifacts=root/'ui';artifacts.mkdir(exist_ok=True);checks=[]

def record(name):
 checks.append(name);(artifacts/'result.json').write_text(json.dumps({'checks':checks,'complete':False},ensure_ascii=False,indent=2),encoding='utf-8');print('PASS '+name,flush=True)

with sync_playwright() as p:
 browser=p.chromium.launch(channel='chrome',headless=True)
 def session(token,width=1440):
  context=browser.new_context(viewport={'width':width,'height':1000});context.add_cookies([{'name':'Admin-Token','value':token,'url':base}]);return context,context.new_page()
 def go(page,path):
  page.goto(base+path);page.wait_for_load_state('networkidle');expect(page.locator('#app')).to_be_visible();assert page.url==base+path,page.url
 def field(container,label):
  return container.locator('.el-form-item').filter(has=container.page.locator('.el-form-item__label').filter(has_text=re.compile('^'+re.escape(label)+'$'))).first
 def select(page,control,label):
  control.click();page.locator('.el-select-dropdown:visible').get_by_role('option',name=label,exact=True).last.click()
 context,page=session(admin)
 errors=[];page.on('pageerror',lambda error:errors.append(str(error)))
 for width in [1920,1440,768,390]:
  page.set_viewport_size({'width':width,'height':1000})
  for route in ['product','material','process','production-line','warehouse-order','quote-rule','quotation']:
   go(page,'/metal/'+route)
   assert page.evaluate('document.documentElement.scrollWidth<=window.innerWidth+2'),f'page overflow {width} {route}'
   if route in ['product','material','process','production-line','warehouse-order']:
    styles=page.locator('.el-table__header th .cell,.el-table__body td .cell').evaluate_all('(cells)=>cells.slice(0,60).map(n=>getComputedStyle(n).textAlign)')
    assert all(s=='center' for s in styles),f'centering {route}: {styles}'
   page.screenshot(path=str(artifacts/f'{route}-{width}.png'),full_page=True)
  record(f'{width}px pages fit viewport; tables scroll within container and cells centered')
 page.set_viewport_size({'width':1440,'height':1000});go(page,'/metal/product');page.get_by_role('button',name='添加',exact=True).click();dialog=page.get_by_role('dialog');expect(dialog).to_be_visible()
 select(page,field(dialog,'所属工厂').locator('.el-select'),fixture['prefix']+'厂A')
 metal=dialog.locator('.metal-type-control .el-select');select(page,metal,'铱');expect(metal).to_contain_text('铱')
 dialog.get_by_role('button',name='添加工序',exact=True).click();expect(dialog.locator('.process-step')).to_have_count(1)
 select(page,field(dialog,'所属工厂').locator('.el-select'),fixture['prefix']+'厂B');expect(dialog.locator('.process-step')).to_have_count(0);expect(metal).not_to_contain_text('铱')
 dialog.get_by_role('button',name='取消',exact=True).click();record('admin factory switch clears metal and route references')
 go(page,'/metal/quote-rule');select(page,page.locator('.quote-page>.el-form .el-select').first,fixture['prefix']+'厂A')
 expect(page.locator('.rule-card')).to_have_count(1);expect(page.locator('.rule-card')).to_contain_text('理论损失单价');page.screenshot(path=str(artifacts/'factory-rule.png'),full_page=True)
 assert not errors,errors;context.close()

 context,page=session(fixture['accounts'][0]['token']);go(page,'/metal/work-order');page.get_by_role('button',name='添加',exact=True).click();order=page.get_by_role('dialog',name='添加工单');expect(order).to_be_visible()
 select(page,field(order,'产线').locator('.el-select'),fixture['prefix']+'产线')
 select(page,field(order,'选择产品').locator('.el-select'),fixture['prefix']+'产品');expect(order.locator('.plan-table .el-table__body tbody tr')).to_have_count(3)
 quantity=order.get_by_placeholder('请输入产品数量');quantity.fill('7')
 page.set_viewport_size({'width':390,'height':900});order.get_by_role('button',name='添加客户',exact=True).click();customer=page.get_by_role('dialog',name='添加客户');expect(customer).to_be_visible()
 name=fixture['prefix']+'界面新增'+str(os.getpid())
 for label,value in [('客户名称',name),('客户公司名称','界面公司'),('客户地址','界面新增地址'),('联络人名称','界面联络'),('联络人电话','13812345670')]:customer.get_by_role('textbox',name=label,exact=True).fill(value)
 expect(customer.get_by_role('textbox',name='部门',exact=True)).to_have_value('')
 box=customer.bounding_box();assert box and box['x']>=0 and box['x']+box['width']<=392
 page.screenshot(path=str(artifacts/'inline-customer-390.png'),full_page=True)
 with page.expect_response(lambda r:'/metal/customer' in r.url and r.request.method=='POST') as response:customer.get_by_role('button',name='保存',exact=True).click()
 saved=response.value.json();assert saved['code']==200,saved
 expect(customer).not_to_be_visible();expect(field(order,'客户名称')).to_contain_text(name);expect(quantity).to_have_value('7');expect(order.locator('.plan-table .el-table__body tbody tr')).to_have_count(3)
 expect(field(order,'客户地址').locator('input')).to_have_value('界面新增地址');expect(field(order,'联络人电话').locator('input')).to_have_value('13812345670')
 page.screenshot(path=str(artifacts/'inline-customer-preserved.png'),full_page=True);record('inline customer saved with empty department; selected and full data filled; product, quantity and 3-step draft preserved')
 # A failed response leaves the draft in place; an unknown outcome locks repeat submission.
 order.get_by_role('button',name='添加客户',exact=True).click();expect(customer).to_be_visible()
 for label,value in [('客户名称',name+'未知'),('客户公司名称','界面公司'),('客户地址','结果待核对'),('联络人名称','界面联络'),('联络人电话','13812345670')]:customer.get_by_role('textbox',name=label,exact=True).fill(value)
 page.route('**/dev-api/metal/customer',lambda route:route.abort('failed') if route.request.method=='POST' else route.continue_())
 customer.get_by_role('button',name='保存',exact=True).click();expect(customer).to_contain_text('创建结果待确认');expect(customer.get_by_role('button',name='保存',exact=True)).to_be_disabled();expect(order.get_by_role('button',name='取消',exact=True)).to_be_disabled()
 expect(customer.get_by_role('textbox',name='客户名称',exact=True)).to_have_value(name+'未知');customer.get_by_role('button',name='已核对客户列表，关闭草稿').click();expect(customer).not_to_be_visible();expect(quantity).to_have_value('7');record('unknown inline creation outcome retains input, prevents retries and blocks parent close until acknowledgement')
 page.unroute('**/dev-api/metal/customer');missing_id_requests=[]
 def missing_id(route):
  if route.request.method=='POST':missing_id_requests.append(route.request.url);route.fulfill(status=200,content_type='application/json',body='{"code":200}')
  else:route.continue_()
 page.route('**/dev-api/metal/customer',missing_id)
 order.get_by_role('button',name='添加客户',exact=True).click();expect(customer).to_be_visible()
 for label,value in [('客户名称',name+'无编号'),('客户公司名称','界面公司'),('客户地址','缺少编号待核对'),('联络人名称','界面联络'),('联络人电话','13812345670')]:customer.get_by_role('textbox',name=label,exact=True).fill(value)
 customer.get_by_role('button',name='保存',exact=True).click();expect(customer).to_contain_text('创建结果待确认');expect(customer.get_by_role('button',name='保存',exact=True)).to_be_disabled();assert len(missing_id_requests)==1
 expect(customer.get_by_role('textbox',name='客户名称',exact=True)).to_have_value(name+'无编号');customer.get_by_role('button',name='已核对客户列表，关闭草稿').click();expect(customer).not_to_be_visible();expect(quantity).to_have_value('7');record('HTTP200 without customer ID stays unknown; draft retained and only one create request sent')
 context.close();browser.close()
(artifacts/'result.json').write_text(json.dumps({'checks':checks,'complete':True},ensure_ascii=False,indent=2),encoding='utf-8')
