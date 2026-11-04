"""Real browser checks against the registered, isolated 8091/5181 fixture."""
import json
import re
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / '.runtime/warehouse-flow-20261103'
data = json.loads((RUNTIME / 'browser-private.json').read_text(encoding='utf-8'))
BASE = 'http://127.0.0.1:5181'
checks = []

def passed(name):
    checks.append(name)
    print('PASS ' + name.encode('ascii', 'backslashreplace').decode('ascii'))

def field(dialog, label):
    return dialog.locator('.el-form-item').filter(has=dialog.page.locator('label').filter(has_text=re.compile('^' + re.escape(label) + '$')))

def select(page, control, text=None):
    control.locator('.el-select').first.click()
    options = page.locator('.el-select-dropdown:visible .el-select-dropdown__item')
    if text is not None:
        options = options.filter(has_text=text)
    expect(options.first).to_be_visible()
    options.first.click()

def pending(page):
    page.get_by_role('button', name='仓储交接待办', exact=True).click()
    dialog = page.get_by_role('dialog', name='仓储交接确认', exact=True)
    expect(dialog.locator('.el-table__row').first).to_be_visible()
    dialog.locator('.el-table__row').first.get_by_role('button', name='查看', exact=True).click()
    expect(dialog.locator('.flow-group').first).to_be_visible()
    return dialog

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    contexts = []
    errors = []
    def login(token):
        context = browser.new_context(viewport={'width': 1440, 'height': 1000})
        contexts.append(context)
        context.add_cookies([{'name': 'Admin-Token', 'value': token, 'url': BASE}])
        page = context.new_page()
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto(BASE + '/metal/inventory')
        page.wait_for_load_state('networkidle')
        expect(page.locator('.warehouse-table')).to_be_visible()
        return page
    try:
        admin = login(data['admin'])
        raw_rows = admin.locator('.warehouse-table .el-table__body .el-table__row').filter(has_text='WH_M1_' + data['suffix'])
        expect(raw_rows).to_have_count(2)
        for width in [1920, 1440, 768, 390]:
            admin.set_viewport_size({'width': width, 'height': 1000})
            table = admin.locator('.warehouse-table')
            headers = table.locator('th').all_text_contents()
            assert not any('数量' in h or '件' in h for h in headers), headers
            assert any('工厂自有' in t for t in raw_rows.all_text_contents())
            customer_row = raw_rows.filter(has_text=data['prefix'] + '公司A')
            customer_row.get_by_role('button', name='查看详情', exact=True).click()
            dialog = admin.get_by_role('dialog', name='库存管理详情', exact=True)
            expect(dialog).to_contain_text('剩余总量：90.00 g')
            expect(dialog).to_contain_text('待确认占用：90.00 g')
            assert '数量' not in dialog.inner_text() and '件' not in dialog.inner_text()
            admin.screenshot(path=str(RUNTIME / f'raw-detail-{width}.png'), full_page=True, animations='disabled')
            assert admin.evaluate('document.documentElement.scrollWidth <= window.innerWidth'), width
            dialog.get_by_role('button', name='关闭', exact=True).click()
            passed(f'raw detail, reservations and no piece units at {width}px')
        admin.set_viewport_size({'width': 1440, 'height': 1000})
        dialog = pending(admin)
        expect(dialog.get_by_role('button', name='本人实领确认', exact=True)).to_have_count(0)
        passed('administrator has no proxy signing button')
        dialog.get_by_role('button', name='关闭', exact=True).click()
        processor = login(data['processor'])
        dialog = pending(processor)
        # A lost successful response must retry the same stage UUID.
        calls = [0]
        def lose_first(route):
            calls[0] += 1
            if calls[0] == 1:
                response = route.fetch()
                assert response.json()['code'] == 200
                route.abort('failed')
            else:
                route.continue_()
        processor.route('**/dev-api/metal/warehouse-flows/*/groups/*/confirm', lose_first)
        dialog.get_by_role('button', name='本人实领确认', exact=True).click()
        expect(dialog.locator('.el-alert')).to_be_visible()
        expect(dialog.get_by_role('button', name='本人实领确认', exact=True)).to_be_enabled()
        dialog.get_by_role('button', name='本人实领确认', exact=True).click()
        expect(dialog).to_contain_text('已生效')
        assert calls[0] == 2
        processor.unroute('**/dev-api/metal/warehouse-flows/*/groups/*/confirm', lose_first)
        expect(processor.locator('.el-message--error').first).not_to_be_visible(timeout=10000)
        passed('actual recipient retries lost response without duplicate movement')
        dialog.get_by_role('button', name='关闭', exact=True).click()
        processor.get_by_role('button', name='加工产出交接', exact=True).click()
        handover = processor.get_by_role('dialog', name='加工产出及全部余料交接', exact=True)
        select(processor, field(handover, '本人已实领的加工批次'))
        expect(handover.locator('.el-table__body .el-table__row')).to_have_count(1)
        output = handover.locator('.el-table__body .el-table__row').first
        select(processor, output.locator('td').nth(1), data['prefix'] + '负责人A')
        output.locator('td').nth(2).locator('input').fill('96')
        handover.get_by_text('本次全部余料一并交接', exact=True).click()
        for original_weight, returned_weight in [('90.00', '2'), ('10.00', '1')]:
            handover.get_by_role('button', name='追加未用料', exact=True).click()
            row = handover.locator('.return-row').last
            select(processor, field(row, '本人原实领明细'), original_weight + 'g')
            select(processor, field(row, '指定仓管员'), data['prefix'] + '负责人A')
            field(row, '加工人还料称重（g）').locator('input').fill(returned_weight)
            assert '件数' not in row.inner_text()
        field(handover, '交接说明 / 回收原因').locator('textarea').fill('浏览器双称重固定验收')
        handover.get_by_text('已逐项核对本人的全部产出及全部余料，或明确无余料', exact=True).click()
        for width in [1920, 1440, 768, 390]:
            processor.set_viewport_size({'width': width, 'height': 1000})
            processor.screenshot(path=str(RUNTIME / f'handover-{width}.png'), full_page=True, animations='disabled')
            assert processor.evaluate('document.documentElement.scrollWidth <= window.innerWidth'), width
        processor.set_viewport_size({'width': 1440, 'height': 1000})
        handover.get_by_role('button', name='本人提交全部交接', exact=True).click()
        expect(handover).not_to_be_visible()
        dialog = processor.get_by_role('dialog', name='仓储交接确认', exact=True)
        expect(dialog).to_contain_text('待指定库管')
        expect(dialog.get_by_role('button', name='本人库管验收', exact=True)).to_have_count(0)
        passed('processor declares output and both raw residue owners across four widths')
        keeper = login(data['keeper'])
        dialog = pending(keeper)
        finished = dialog.locator('.flow-group').filter(has_text='成品入库')
        finished.locator('.el-table__body .el-table__row').locator('td').nth(3).locator('input').fill('95.8')
        finished.get_by_placeholder('重量不同必须填写').fill('库管复称短少0.2g')
        finished.get_by_role('button', name='本人库管验收', exact=True).click()
        expect(dialog).to_contain_text('待其余人员确认')
        raw = dialog.locator('.flow-group').filter(has_text='原料还库')
        raw.get_by_role('button', name='本人库管验收', exact=True).click()
        expect(dialog).to_contain_text('已生效')
        keeper.screenshot(path=str(RUNTIME / 'keeper-posted.png'), full_page=True, animations='disabled')
        passed('designated keeper independently accepts 95.8g and all residues atomically')
        assert not errors, errors
        (RUNTIME / 'ui-result.json').write_text(json.dumps({'passed': len(checks), 'checks': checks, 'pageErrors': errors}, ensure_ascii=False, indent=2), encoding='utf-8')
    finally:
        for context in contexts:
            context.close()
        browser.close()
