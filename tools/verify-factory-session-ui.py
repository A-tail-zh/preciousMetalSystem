"""Cookie-only account changes must not submit another account's open draft."""
from pathlib import Path
import json,time
from playwright.sync_api import sync_playwright,expect

root=Path(__file__).resolve().parents[1]/'.runtime/factory-scope-20261104'
manifest=json.loads((root/'last-live.json').read_text(encoding='utf-8'));folder=root/('live-'+manifest['prefix'].removeprefix('工单验收_'));fixture=json.loads((folder/'browser.private.json').read_text(encoding='utf-8'))
assert fixture['environment']['api']=='http://127.0.0.1:8093'
first,second=fixture['accounts'][0]['token'],fixture['accounts'][2]['token'];base='http://127.0.0.1:5197';results=[]
with sync_playwright() as p:
 browser=p.chromium.launch(channel='chrome',headless=True)
 def context():
  c=browser.new_context(viewport={'width':1440,'height':1000});c.add_cookies([{'name':'Admin-Token','value':first,'url':base}]);return c,c.new_page()
 def switch(c):c.add_cookies([{'name':'Admin-Token','value':second,'url':base}])
 def gate(page,selector):
  page.evaluate('''selector=>{let instance=document.querySelector(selector).__vueParentComponent;while(instance&&!instance.exposed?.validate)instance=instance.parent;if(!instance)throw Error('form validation not exposed');const original=instance.exposed.validate;instance.exposed.validate=async(...args)=>{const valid=await original(...args);window.validationWaiting=true;await new Promise(resolve=>window.validationResume=resolve);return valid}}''',selector)
 for action in ['volume','rule','delete','metal']:
  c,page=context();writes=[]
  def intercept(route):
   if route.request.method in ['POST','PUT','DELETE']:
    writes.append({'method':route.request.method,'url':route.request.url});route.fulfill(status=200,content_type='application/json',body=json.dumps({'code':200,'id':'999999'}))
   else:route.continue_()
  page.route('**/dev-api/metal/**',intercept)
  try:
   page.goto(base+'/metal/quote-rule');page.wait_for_load_state('networkidle');expect(page.locator('.rule-card')).to_have_count(1)
   if action=='volume':gate(page,'.volume-form');page.get_by_role('button',name='保存参数',exact=True).click()
   elif action=='rule':page.locator('.rule-card').get_by_role('button',name='编辑',exact=True).click();expect(page.get_by_role('dialog',name='编辑报价规则')).to_be_visible();gate(page,'.rule-form');page.get_by_role('button',name='保存配置',exact=True).click()
   elif action=='delete':page.locator('.rule-card').get_by_role('button',name='删除',exact=True).click();expect(page.locator('.el-message-box')).to_be_visible()
   else:
    page.get_by_role('button',name='增加报价',exact=True).click();page.get_by_role('button',name='添加金属类型',exact=True).click();metal=page.get_by_role('dialog',name='添加金属类型');expect(metal).to_be_visible();metal.get_by_role('textbox').fill('会话防错'+str(time.time_ns()))
   if action in ['volume','rule']:page.wait_for_function('window.validationWaiting===true')
   switch(c)
   if action in ['volume','rule']:page.evaluate('window.validationResume()')
   elif action=='delete':page.locator('.el-message-box').get_by_role('button',name='删除',exact=True).click()
   else:metal.get_by_role('button',name='保存',exact=True).click()
   page.wait_for_timeout(300);assert not writes,f'{action} submitted stale-account draft: {writes}'
   results.append({'case':action,'pass':True});print('PASS cookie-only switch blocks '+action,flush=True)
  except Exception as error:results.append({'case':action,'pass':False,'error':str(error)});print('FAIL '+action+': '+str(error)[:350],flush=True)
  finally:c.close()
 # A response requested under A is ignored after the browser cookie changes to B.
 c,page=context();pending=[]
 def hold(route):pending.append((route,route.fetch()))
 page.route('**/dev-api/metal/quote-rule/list*',hold)
 try:
  page.goto(base+'/metal/quote-rule',wait_until='domcontentloaded')
  for _ in range(80):
   if pending:break
   page.wait_for_timeout(50)
  assert pending,'rule read was not observed';switch(c)
  for route,response in pending:route.fulfill(response=response)
  page.wait_for_timeout(300);expect(page.locator('.rule-card')).to_have_count(0)
  results.append({'case':'late-read','pass':True});print('PASS stale account response ignored',flush=True)
 except Exception as error:results.append({'case':'late-read','pass':False,'error':str(error)});print('FAIL late-read: '+str(error)[:300],flush=True)
 finally:c.close()
 # Same-account save still reaches the real isolated server successfully.
 c,page=context()
 try:
  page.goto(base+'/metal/quote-rule');page.wait_for_load_state('networkidle');expect(page.get_by_role('button',name='保存参数',exact=True)).to_be_enabled()
  with page.expect_response(lambda r:'/metal/volume-rule' in r.url and r.request.method=='PUT') as response:page.get_by_role('button',name='保存参数',exact=True).click()
  assert response.value.json()['code']==200
  results.append({'case':'same-account','pass':True});print('PASS same-account parameter save succeeds',flush=True)
 except Exception as error:results.append({'case':'same-account','pass':False,'error':str(error)});print('FAIL same-account: '+str(error)[:300],flush=True)
 finally:c.close();browser.close()
(root/'session-ui-result.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
assert all(row['pass'] for row in results),'Account-context regression failed'
