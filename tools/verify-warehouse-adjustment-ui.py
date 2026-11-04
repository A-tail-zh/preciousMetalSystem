import json
import re
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

ROOT=Path(__file__).resolve().parents[1]
RUNTIME=ROOT/'.runtime/warehouse-flow-20261103'
data=json.loads((RUNTIME/'browser-private.json').read_text(encoding='utf-8'))
BASE='http://127.0.0.1:5181'
checks=[]
def field(page,dialog,label):
    return dialog.locator('.el-form-item').filter(has=page.locator('label').filter(has_text=re.compile('^'+re.escape(label)+'$')))
def select(page,control,text):
    control.locator('.el-select__wrapper').click()
    popup=control.get_by_role('combobox').get_attribute('aria-controls')
    option=page.locator('[id="'+popup+'"]').get_by_role('option').filter(has_text=text).first
    expect(option).to_be_visible();option.click()
with sync_playwright() as p:
    browser=p.chromium.launch(channel='chrome',headless=True)
    context=browser.new_context(viewport={'width':1440,'height':1000})
    context.add_cookies([{'name':'Admin-Token','value':data['admin'],'url':BASE}])
    page=context.new_page();errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
    try:
        page.goto(BASE+'/metal/inventory');page.wait_for_load_state('networkidle')
        row=page.locator('.warehouse-table .el-table__row').filter(has_text=data['prefix']+'公司A')
        expect(row).to_have_count(1)
        row.get_by_role('button',name='库存调整',exact=True).click()
        dialog=page.get_by_role('dialog',name='库存调整',exact=True)
        expect(dialog).to_contain_text('当前净重：90.00 g')
        for width in [1920,1440,768,390]:
            page.set_viewport_size({'width':width,'height':1000})
            assert not any(s in dialog.inner_text() for s in ['数量','件数','毛重差额','杂质差额'])
            assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
            page.screenshot(path=str(RUNTIME/f'adjustment-{width}.png'),full_page=True,animations='disabled')
            checks.append(f'raw adjustment fields at {width}px')
        page.set_viewport_size({'width':1440,'height':1000})
        field(page,dialog,'净重差额(g)').locator('input').fill('5')
        for label in ['经手人','仓管员']:
            select(page,field(page,dialog,label),data['prefix']+'负责人A')
        field(page,dialog,'调整原因').locator('textarea').fill('本轮浏览器响应丢失重试')
        calls=[0]
        def lost(route):
            calls[0]+=1
            if calls[0]==1:
                response=route.fetch();assert response.json()['code']==200;route.abort('failed')
            else:route.continue_()
        page.route('**/dev-api/metal/inventory/'+data['customerStock']+'/adjust',lost)
        dialog.get_by_role('button',name='确认',exact=True).click()
        expect(dialog.get_by_role('button',name='确认',exact=True)).to_be_enabled()
        expect(field(page,dialog,'调整原因').locator('textarea')).to_have_value('本轮浏览器响应丢失重试')
        dialog.get_by_role('button',name='确认',exact=True).click()
        expect(dialog).not_to_be_visible();expect(row).to_contain_text('95.00');assert calls[0]==2
        page.unroute('**/dev-api/metal/inventory/'+data['customerStock']+'/adjust',lost)
        checks.append('successful adjustment retry retains input and replays original UUID')
        row.get_by_role('button',name='库存调整',exact=True).click()
        field(page,dialog,'净重差额(g)').locator('input').fill('-5')
        for label in ['经手人','仓管员']:select(page,field(page,dialog,label),data['prefix']+'负责人A')
        field(page,dialog,'调整原因').locator('textarea').fill('本轮负差额还原')
        dialog.get_by_role('button',name='确认',exact=True).click();expect(dialog).not_to_be_visible();expect(row).to_contain_text('90.00')
        checks.append('negative adjustment respects reserved balance and restores 90g')
        assert not errors,errors
        (RUNTIME/'adjustment-ui-result.json').write_text(json.dumps({'passed':len(checks),'checks':checks,'pageErrors':errors},indent=2),encoding='utf-8')
        print('PASS '+str(len(checks))+' real browser raw adjustment checks')
    finally:
        context.close();browser.close()
