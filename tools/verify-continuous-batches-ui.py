"""Read-only browser verification against the registered continuous-batch fixture."""
import json
from pathlib import Path
from playwright.sync_api import sync_playwright

root = Path(__file__).resolve().parents[1]
run = root / '.runtime/continuous-batches-20261104'
fixture = json.loads((run / 'browser.private.json').read_text(encoding='utf-8-sig'))
assert fixture['schema'] == 'heavy_metal_continuous_20261104'
checks, writes, errors = [], [], []
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    context = browser.new_context()
    context.add_cookies([{'name': 'Admin-Token', 'value': fixture['token'], 'url': 'http://127.0.0.1:5216'}])
    page = context.new_page()
    page.on('pageerror', lambda error: errors.append(str(error)))

    def readonly(route):
        if route.request.method not in ['GET', 'HEAD', 'OPTIONS']:
            writes.append(route.request.url)
            route.abort()
        else:
            route.continue_()

    page.route('**/dev-api/**', readonly)
    page.goto('http://127.0.0.1:5216/metal/work-order')
    page.wait_for_load_state('networkidle')
    page.locator('#app').wait_for()
    page.evaluate("""async fixture=>{
        const {createVNode,render}=await import('/node_modules/.vite/deps/vue.js');
        const host=document.createElement('div');host.id='real-batch-detail';host.style.cssText='position:fixed;inset:0;overflow:auto;z-index:3000;background:white';document.body.appendChild(host);
        const vnode=createVNode((await import('/src/views/metal/work-order/WorkOrderDetail.vue')).default,{id:fixture.workOrderId});
        vnode.appContext=document.querySelector('#app').__vue_app__._context;render(vnode,host);
    }""", fixture)
    detail = page.locator('#real-batch-detail')
    detail.get_by_text('加工批次进度', exact=True).wait_for()
    detail.get_by_text('已验收／已结算', exact=True).first.wait_for()
    for width in [1920, 1440, 768, 390]:
        page.set_viewport_size({'width': width, 'height': 1000})
        page.wait_for_timeout(150)
        assert detail.locator('.batch-progress').is_visible()
        assert detail.locator('.batch-progress').get_by_text('50', exact=True).count() > 0
        page.screenshot(path=str(run / f'real-work-order-{width}.png'))
        checks.append(f'Real work-order batch summary, statuses and remaining 50 pieces at {width}px')
    page.evaluate("""async fixture=>{
        document.querySelector('#real-batch-detail').style.display='none';
        const {createVNode,render,h,ref}=await import('/node_modules/.vite/deps/vue.js');
        const component=(await import('/src/views/metal/warehouse-common/ProductionBatchChoice.vue')).default;
        const host=document.createElement('div');host.id='real-batch-choice';host.style.cssText='position:fixed;inset:20px;z-index:3001;background:white;padding:16px';document.body.appendChild(host);
        const wrapper={setup(){const model=ref({batchAction:'NEW',batchId:null,batchVersion:null,plannedQuantity:''}),child=ref();window.batchChoiceTest={model,child};return()=>h(component,{ref:child,factoryId:fixture.factoryId,workOrderId:fixture.workOrderId,itemId:fixture.itemId,processId:fixture.processId,modelValue:model.value,'onUpdate:modelValue':value=>model.value=value,disabled:false,isContextCurrent:()=>true})}};
        const vnode=createVNode(wrapper);vnode.appContext=document.querySelector('#app').__vue_app__._context;render(vnode,host);
    }""", fixture)
    choice = page.locator('#real-batch-choice')
    choice.get_by_text('本工序剩余可安排 50 件；待验收批次仍占用计划额度。', exact=True).wait_for()
    choice.get_by_placeholder('填写本批件数，不是整单剩余件数').fill('40')
    assert page.evaluate('window.batchChoiceTest.child.value.validate()')
    choice.get_by_placeholder('填写本批件数，不是整单剩余件数').fill('60')
    assert not page.evaluate('window.batchChoiceTest.child.value.validate()')
    checks.append('Real remaining capacity accepts 40 and rejects 60 without posting')
    browser.close()
assert not writes, writes
assert not errors, errors
(run / 'real-ui-result.json').write_text(json.dumps({'complete': True, 'checks': checks, 'businessWrites': writes, 'pageErrors': errors}, ensure_ascii=False, indent=2), encoding='utf-8')
print('PASS', len(checks), 'real UI checks; zero business writes, zero page errors')
