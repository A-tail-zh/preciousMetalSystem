import {test} from './helpers/isolated-live-test'
import {verifyLiveQuotationDownload} from './helpers/live-quotation-download'

test('保存报价单后下载Excel，数据使用快照且跨厂拒绝',async({page,context})=>{
 test.skip(process.env.LIVE_DASHBOARD!=='1','需要真实后端');test.setTimeout(120000)
 await verifyLiveQuotationDownload(page,context,'杆',{innerDiameter:'0',outerDiameter:'2',length:'3'})
})
