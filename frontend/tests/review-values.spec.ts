import {test,expect} from '@playwright/test'
import {reviewLines,actualTotal} from '../src/views/metal/storage-live/review-values'
test('review totals are exact, no missing number becomes zero, snapshots stay unchanged',()=>{
 const line={netWeight:'0.30',actualNetWeight:'0.30',measurementMode:'WEIGHT',differenceMode:'REWEIGH',allocations:[{weight:'0.10',receivedWeight:'0.10'},{weight:'0.20',receivedWeight:'0.20'}]};const before=JSON.stringify(line);expect(actualTotal(line)).toBe('0.30');expect(reviewLines([line],'PRODUCTION_OUT')[0].actualNetWeight).toBe('0.30');expect(JSON.stringify(line)).toBe(before);line.allocations[0].receivedWeight='';expect(actualTotal(line)).toBe('');expect(()=>reviewLines([line],'PRODUCTION_OUT')).toThrow('重量须为非负数');line.allocations[0].receivedWeight='0.101';expect(()=>reviewLines([line],'PRODUCTION_OUT')).toThrow('重量须为非负数')
})
test('single source derives only from main values and requires precise difference reason',()=>{
 const line={netWeight:'10',quantity:10,actualNetWeight:'9',actualQuantity:'9',measurementMode:'PIECE',differenceMode:'LOSS',lossBearer:'FACTORY',allocations:[{weight:'10',quantity:10,receivedWeight:'1',receivedQuantity:1}]};expect(()=>reviewLines([line],'PRODUCTION_OUT')).toThrow('本行差异原因');const result=reviewLines([{...line,differenceReason:'缺件'}],'PRODUCTION_OUT')[0];expect(result.allocations[0]).toMatchObject({weight:'10.00',receivedWeight:'9.00',quantity:'10',receivedQuantity:'9',lossBearer:'FACTORY'});expect(line.allocations[0].receivedWeight).toBe('1')
})
