import {test} from './helpers/isolated-live-test'
import {verifyLiveQuotationDownload} from './helpers/live-quotation-download'

test('铱环报价实际下载包含尺寸图片且跨厂拒绝',async({page,context})=>{
 test.skip(process.env.LIVE_DASHBOARD!=='1','需要真实后端');test.setTimeout(120000)
 await verifyLiveQuotationDownload(page,context,'环',{innerDiameter:'1',outerDiameter:'2',thickness:'1'})
})
