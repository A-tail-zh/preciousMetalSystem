import {test} from './helpers/isolated-live-test'
import {verifyLiveQuotationDownload} from './helpers/live-quotation-download'

test('铱坩埚平底报价实际下载包含尺寸图片且跨厂拒绝',async({page,context})=>{
 test.skip(process.env.LIVE_DASHBOARD!=='1','需要真实后端');test.setTimeout(120000)
 await verifyLiveQuotationDownload(page,context,'平底坩埚',{innerDiameter:'1',outerDiameter:'2',wallThickness:'0.5',innerHeight:'3',bottomThickness:'0.5'})
})
