"""All API calls are mocked; run against the inventory owner's existing isolated Vite."""
import argparse
import json
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright, expect

parser = argparse.ArgumentParser()
parser.add_argument('--base-url', default='http://127.0.0.1:5197')
args = parser.parse_args()
base = args.base_url.rstrip('/')
assert urlparse(base).hostname in ('127.0.0.1', 'localhost')


def overview(closed=False, raw=False):
    value = {
        'workOrder': {'status': 'COMPLETED' if closed else 'IN_PROGRESS', 'taskType': 'RAW_PROCESSING' if raw else 'PRODUCTION', 'processingPolicyVersion': 3, 'version': 1},
        'items': [
            {'itemId': '10', 'productSnapshot': json.dumps({'name': '新版产品', 'measurementPolicyVersion': 3}), 'materialSnapshot': {'name': '原料任务', 'measurementPolicyVersion': 3}, 'plannedQuantity': 10, 'completedQuantity': 7},
            {'itemId': '11', 'productSnapshot': {'name': '历史产品', 'measurementPolicyVersion': 2}, 'plannedQuantity': 10, 'completedQuantity': 0},
            {'itemId': '12', 'productSnapshot': {'name': '未标记历史产品'}, 'plannedQuantity': 10, 'completedQuantity': 0},
        ],
        'progress': [
            {'itemId': '10', 'processId': '101', 'processName': '新版前处理', 'outputStage': 'RAW', 'measurementMode': 'PIECE', 'localQuantity': 7, 'externalQuantity': 0, 'skippedQuantity': 0, 'coveredQuantity': 7, 'outputWeight': '70', 'weightStageClosed': False},
            {'itemId': '10', 'processId': '102', 'processName': '新版重量工序', 'outputStage': 'SEMI', 'measurementMode': 'WEIGHT', 'outputWeight': '60', 'weightStageClosed': False},
            {'itemId': '11', 'processId': '201', 'processName': '历史前处理', 'outputStage': 'RAW', 'measurementMode': 'PIECE', 'localQuantity': 0, 'outputWeight': '50', 'weightStageClosed': False},
            {'itemId': '12', 'processId': '301', 'processName': '未标记前处理', 'outputStage': 'RAW', 'measurementMode': 'PIECE', 'localQuantity': 0, 'outputWeight': '40', 'weightStageClosed': False},
        ],
        'stocks': [
            {'inventoryId': '1', 'name': '新版原料库存', 'category': 'RAW', 'measurementMode': 'PIECE', 'effectiveMeasurementMode': 'PIECE', 'quantity': 7, 'netWeight': '70', 'unreleasedQuantity': 7, 'unreleasedWeight': '70'},
            {'inventoryId': '2', 'name': '历史原料库存', 'category': 'RAW', 'measurementMode': 'PIECE', 'effectiveMeasurementMode': 'WEIGHT', 'quantity': 99, 'netWeight': '50', 'unreleasedQuantity': 99, 'unreleasedWeight': '50'},
            {'inventoryId': '3', 'name': '重量半成品库存', 'category': 'SEMI', 'measurementMode': 'WEIGHT', 'effectiveMeasurementMode': 'WEIGHT', 'quantity': 0, 'netWeight': '60', 'referenceQuantity': 11, 'referenceComplete': True, 'unreleasedWeight': '60'},
            {'inventoryId': '4', 'name': '缺少有效模式原料', 'category': 'RAW', 'measurementMode': 'PIECE', 'quantity': 88, 'netWeight': '40', 'unreleasedQuantity': 88, 'unreleasedWeight': '40'},
        ],
        'releases': [], 'uses': [], 'closures': [],
    }
    if raw:
        value['items'] = value['items'][:1]
        value['progress'] = value['progress'][:1]
        value['progress'][0].update(measurementMode='WEIGHT', localQuantity=None)
        value['stocks'] = [value['stocks'][1]]
        value['stocks'][0]['quantity'] = 0
    return value


def mount(page, context, payload):
    context.add_cookies([{'name': 'Admin-Token', 'value': 'mock-workorder-progress', 'url': base}])
    writes = []

    def route(request):
        path = urlparse(request.request.url).path.removeprefix('/dev-api')
        if request.request.method != 'GET':
            writes.append(path)
            request.abort()
            return
        response = {'code': 200, 'data': [], 'rows': [], 'total': 0}
        if path == '/getInfo':
            response = {'code': 200, 'user': {'userId': 1, 'userName': 'mock'}, 'roles': ['admin'], 'permissions': ['*:*:*']}
        elif path == '/metal/work-order/1/semi-flow':
            response['data'] = payload
        request.fulfill(status=200, content_type='application/json', body=json.dumps(response))

    page.route('**/dev-api/**', route)
    page.goto(base + '/index')
    page.wait_for_load_state('networkidle')
    page.evaluate('''async () => {
      const user=(await import('/src/store/modules/user.ts')).default();
      Object.assign(user,{token:'mock-workorder-progress',id:1,sessionReady:true,permissions:['*:*:*']});
      const vue=await import('/node_modules/.vite/deps/vue.js');
      const component=(await import('/src/views/metal/work-order/SemiFlowPanel.vue')).default;
      const host=document.createElement('div');host.id='workorder-progress-probe';document.body.append(host);
      const vnode=vue.createVNode(component,{workOrderId:'1'});vnode.appContext=document.querySelector('#app').__vue_app__._context;
      vue.render(vnode,host);
    }''')
    panel = page.locator('#workorder-progress-probe')
    expect(panel.get_by_text('新版前处理', exact=True)).to_be_visible()
    return panel, writes


results = []
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in (1920, 1440, 768, 390):
        for case in ('active', 'closed', 'raw-task'):
            context = browser.new_context(viewport={'width': width, 'height': 1000})
            try:
                page = context.new_page()
                panel, writes = mount(page, context, overview(closed=case == 'closed', raw=case == 'raw-task'))
                progress = panel.locator('.el-table').nth(0)
                piece = progress.get_by_role('row').filter(has_text='新版前处理')
                expect(piece.get_by_role('cell').nth(2)).to_have_text('按重量' if case == 'raw-task' else '按件')
                if case != 'raw-task':
                    expect(piece.get_by_role('cell').nth(3)).to_have_text('7件')
                    expect(piece.get_by_role('button', name='结束投产', exact=True)).to_have_count(0)
                if case == 'active':
                    expect(progress.get_by_role('row').filter(has_text='新版重量工序').get_by_role('button', name='结束投产', exact=True)).to_be_visible()
                    expect(progress.get_by_role('row').filter(has_text='历史前处理').get_by_role('cell').nth(2)).to_have_text('按重量')
                    expect(progress.get_by_role('row').filter(has_text='未标记前处理').get_by_role('cell').nth(2)).to_have_text('按重量')
                stocks = panel.locator('.el-table').nth(2)
                stock_expectations = [('历史原料库存', '—')]
                if case != 'raw-task':
                    stock_expectations += [('新版原料库存', '7'), ('缺少有效模式原料', '—')]
                for name, quantity in stock_expectations:
                    stock = stocks.get_by_role('row').filter(has_text=name)
                    expect(stock.get_by_role('cell').nth(3)).to_have_text(quantity)
                    expect(stock.get_by_role('button', name='释放', exact=True)).to_have_count(0)
                if case == 'closed':
                    expect(stocks.get_by_role('row').filter(has_text='重量半成品库存').get_by_role('button', name='释放', exact=True)).to_be_visible()
                assert not writes, writes
                assert panel.bounding_box()['width'] <= width
                results.append({'case': case, 'width': width, 'pass': True})
            except Exception as error:
                results.append({'case': case, 'width': width, 'pass': False, 'error': str(error)[:500]})
            finally:
                context.close()
    browser.close()
print(json.dumps(results, ensure_ascii=False))
assert all(row['pass'] for row in results), 'Work-order measurement display regression'
