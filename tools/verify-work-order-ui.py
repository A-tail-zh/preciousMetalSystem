import json, os
from pathlib import Path
from playwright.sync_api import sync_playwright, expect
info=json.loads(os.environ['WORK_ORDER_UI_INFO'])
base='http://127.0.0.1:5178'
output=Path(__file__).resolve().parents[1]/'.runtime/work-order-v2-20261102'
checks=[]
with sync_playwright() as p:
    browser=p.chromium.launch(channel='chrome',headless=True)
    context=browser.new_context()
    context.add_cookies([{'name':'Admin-Token','value':os.environ['WORK_ORDER_UI_TOKEN'],'url':base}])
    page=context.new_page()
    errors=[]
    page.on('pageerror',lambda error:errors.append(str(error)))
    for width in [1920,1440,768,390]:
        page.set_viewport_size({'width':width,'height':1000})
        page.goto(base+'/metal/work-order?id='+info['doneOrder'])
        page.wait_for_load_state('networkidle')
        for selector in ['.basic-card','.product-list','.material-list','.progress','.reports']:
            expect(page.locator(selector)).to_be_visible()
        expect(page.locator('.material-list')).to_contain_text('120.00')
        expect(page.locator('.reports')).to_contain_text('5.00%')
        assert page.evaluate('document.documentElement.scrollWidth<=window.innerWidth')
        page.screenshot(path=str(output/('live-detail-'+str(width)+'.png')),full_page=True)
        checks.append('真实工单详情 '+str(width)+'px')
    page.set_viewport_size({'width':1440,'height':1000})
    page.goto(base+'/metal/work-order')
    page.wait_for_load_state('networkidle')
    row=page.locator('.order-table .el-table__row').filter(has_text=info['waitingNo'])
    row.get_by_role('button',name='编辑',exact=True).click()
    dialog=page.get_by_role('dialog',name='编辑工单',exact=True)
    expect(dialog).to_be_visible()
    expect(dialog.get_by_label('客户公司名称')).to_have_value(info['company'])
    expect(dialog.get_by_label('客户地址')).to_have_attribute('readonly','')
    dialog.get_by_label('加工类型').press('ArrowDown')
    page.get_by_role('option',name='直接定制',exact=True).click()
    with page.expect_response(lambda response:'/metal/work-order' in response.url and response.request.method=='PUT') as response:
        dialog.get_by_role('button',name='确认',exact=True).click()
    assert response.value.json()['code']==200
    expect(dialog).not_to_be_visible()
    checks.append('真实编辑客户快照及加工类型保存')
    page.goto(base+'/metal/work-order?id='+info['waitingOrder'])
    page.wait_for_load_state('networkidle')
    expect(page.locator('.basic-card')).to_contain_text('直接定制')
    checks.append('保存后详情回显加工类型')
    assert not errors,errors
    browser.close()
(output/'live-browser-result.json').write_text(json.dumps({'passed':checks,'failed':0},ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'passed':len(checks),'failed':0},ensure_ascii=False))
