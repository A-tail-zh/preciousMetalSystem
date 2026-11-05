"""UI write rehearsal against the fixture created by verify-partial-handovers-live.mjs."""
import json
from pathlib import Path
from urllib.request import Request, urlopen
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parents[1]
RUN = ROOT / '.runtime/partial-handovers-20261105'
data = json.loads((RUN / 'browser.private.json').read_text(encoding='utf-8'))
assert data['environment'] == {'schema': 'heavy_metal', 'port': '3323', 'api': 'http://127.0.0.1:8096'}
assert data['prefix'].startswith('工单验收_')
BASE = 'http://127.0.0.1:5241'


def api(path, token):
    req = Request(data['environment']['api'] + path, headers={'Authorization': 'Bearer ' + token})
    result = json.load(urlopen(req, timeout=20))
    assert result['code'] == 200, result.get('msg')
    return result['data']


def choose(page, wrapper, label):
    wrapper.click()
    page.get_by_role('option').filter(has_text=label).first.click()


with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    processor, keeper = data['actors'][1], data['actors'][0]
    context = browser.new_context(viewport={'width': 1440, 'height': 1000})
    context.route('**/*', lambda route: route.continue_() if route.request.url.startswith(BASE + '/') else route.abort())
    context.add_cookies([{'name': 'Admin-Token', 'value': processor['token'], 'url': BASE}])
    page = context.new_page()
    work = api('/metal/work-order/' + data['work'], processor['token'])
    assert str(work['factoryId']) == data['factory']
    page.goto(BASE + '/metal/warehouse-order')
    page.wait_for_load_state('networkidle')
    row = page.locator('.warehouse-page .el-table__body tr').filter(has_text=work['orderNo'])
    for _ in range(5):
        if row.count():
            break
        with page.expect_response(lambda r: '/metal/warehouse-order/list' in r.url):
            page.locator('.el-pagination .btn-next').click()
    expect(row).to_have_count(1)
    row.get_by_role('button', name='物料清单', exact=True).click()
    page.get_by_role('button', name='继续交接', exact=True).click()
    dialog = page.get_by_role('dialog', name='加工产出交接', exact=True)
    expect(dialog).to_contain_text('剩余待完成 50 件')
    expect(dialog).to_contain_text('交接前在制 250.00 g')
    expect(dialog.get_by_text('批次安排', exact=True)).to_have_count(0)
    choose(page, dialog.locator('.el-table .el-select__wrapper').nth(0), data['prefix'] + '仓0')
    choose(page, dialog.locator('.el-table .el-select__wrapper').nth(1), data['prefix'] + '人员0')
    choose(page, dialog.locator('.el-table .el-select__wrapper').nth(2), 'CLEAN_' + data['prefix'])
    dialog.get_by_label('加工称重（g）', exact=True).fill('230')
    dialog.get_by_label('实际产出件数', exact=True).fill('50')
    dialog.get_by_label('剩余在制净重（g）', exact=True).fill('0')
    dialog.locator('.el-checkbox').click()
    for width in [1920, 1440, 768, 390]:
        page.set_viewport_size({'width': width, 'height': 1000})
        assert dialog.evaluate('(e)=>e.scrollWidth<=e.clientWidth')
        page.screenshot(path=str(ROOT / f'outputs/partial-handover-filled-{width}.png'), full_page=True)
    page.set_viewport_size({'width': 1440, 'height': 1000})
    with page.expect_response(lambda r: '/metal/warehouse-flows/handover/' in r.url and r.request.method == 'POST') as pending:
        dialog.get_by_role('button', name='提交本次交接', exact=True).click()
    response = pending.value.json()
    assert response['code'] == 200, response.get('msg')
    flow = response['data']
    expect(dialog).not_to_be_visible()
    assert flow['status'] == 'WAITING'
    assert api('/metal/warehouse-flows/handover-context/' + data['batch'], processor['token'])['status'] == 'OPEN'
    context.close()

    context = browser.new_context(viewport={'width': 1440, 'height': 1000})
    context.route('**/*', lambda route: route.continue_() if route.request.url.startswith(BASE + '/') else route.abort())
    context.add_cookies([{'name': 'Admin-Token', 'value': keeper['token'], 'url': BASE}])
    page = context.new_page()
    page.goto(BASE + '/metal/warehouse-order')
    page.wait_for_load_state('networkidle')
    page.get_by_role('button', name='仓储交接待办', exact=True).click()
    confirm = page.get_by_role('dialog', name='仓储交接确认', exact=True)
    pending_row = confirm.locator('.el-table__body tr').filter(has_text=work['orderNo']).filter(has_text=flow['groups'][0]['orderNo'])
    expect(pending_row).to_have_count(1)
    pending_row.get_by_role('button', name='查看', exact=True).click()
    expect(confirm).to_contain_text('本段产出独立入库')
    confirm.get_by_label('实物件数', exact=True).fill('50')
    confirm.get_by_label('库管实收净重（g）', exact=True).fill('230')
    with page.expect_response(lambda r: '/confirm' in r.url and r.request.method == 'POST') as accepted:
        confirm.get_by_role('button', name='本人库管验收', exact=True).click()
    accepted_result = accepted.value.json()
    assert accepted_result['code'] == 200, accepted_result.get('msg')
    assert accepted_result['data']['status'] == 'POSTED'
    assert api('/metal/warehouse-flows/handover-context/' + data['batch'], processor['token'])['status'] == 'SETTLED'
    print(json.dumps({'flowId': flow['id'], 'stage': '50 submitted from original issue and accepted through real keeper UI'}, ensure_ascii=True))
    page.screenshot(path=str(ROOT / 'outputs/partial-handover-keeper.png'), full_page=True)
    browser.close()
