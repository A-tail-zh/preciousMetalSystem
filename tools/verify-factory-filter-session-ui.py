from pathlib import Path
import json
from urllib.parse import urlparse,parse_qs
from playwright.sync_api import sync_playwright,expect
root=Path(__file__).resolve().parents[1]/'.runtime/factory-scope-20261104';manifest=json.loads((root/'last-live.json').read_text(encoding='utf-8'));folder=root/('live-'+manifest['prefix'].removeprefix('工单验收_'));fixture=json.loads((folder/'browser.private.json').read_text(encoding='utf-8'));base='http://127.0.0.1:5197'
assert fixture['environment']['api']=='http://127.0.0.1:8093'
a,b=fixture['accounts'][:2];failures=[]
with sync_playwright() as p:
 browser=p.chromium.launch(channel='chrome',headless=True)
 for delayed in [False,True]:
  c=browser.new_context(viewport={'width':1440,'height':1000});c.add_cookies([{'name':'Admin-Token','value':a['token'],'url':base}]);page=c.new_page();held=[];requests=[]
  def capture(request):
   if '/metal/product/list' in request.url:requests.append(request.url)
  page.on('request',capture)
  def hold(route):
   if delayed and route.request.headers.get('authorization')=='Bearer '+a['token']:held.append((route,route.fetch()))
   else:route.continue_()
  page.route('**/dev-api/metal/options/factories*',hold)
  try:
   page.goto(base+'/metal/product',wait_until='domcontentloaded')
   if delayed:
    for _ in range(100):
     if held:break
     page.wait_for_timeout(50)
    assert held
   else:page.wait_for_load_state('networkidle')
   c.add_cookies([{'name':'Admin-Token','value':b['token'],'url':base}]);requests.clear()
   page.evaluate('''account=>{const store=document.querySelector('#app').__vue_app__.config.globalProperties.$pinia._s.get('user');store.token=account.token;store.id=account.id;store.sessionReady=true}''',b)
   page.wait_for_load_state('networkidle') if not delayed else page.wait_for_timeout(600)
   for route,response in held:route.fulfill(response=response)
   page.wait_for_timeout(300);page.get_by_role('button',name='查询',exact=True).click();page.wait_for_load_state('networkidle')
   factories=[parse_qs(urlparse(url).query).get('factoryId',[None])[0] for url in requests]
   assert fixture['a']['factoryId'] not in factories,{'oldFactoryRequests':factories,'delayed':delayed}
   assert factories and factories[-1]==fixture['b']['factoryId'],{'lastFactory':factories,'delayed':delayed}
   expect(page.locator('.master-table')).to_contain_text(fixture['prefix']+'产品')
   print('PASS factory context rebuilt after account switch; late='+str(delayed),flush=True)
  except Exception as error:failures.append(str(error));print('FAIL delayed='+str(delayed)+': '+str(error)[:350],flush=True)
  finally:c.close()
 browser.close()
(root/'filter-session-ui-result.json').write_text(json.dumps({'failures':failures,'complete':not failures},ensure_ascii=False,indent=2),encoding='utf-8')
assert not failures
