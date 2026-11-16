import {test,expect} from '@playwright/test';
import {adjustmentNet} from '../src/utils/inventory-adjustment';
test('signed inventory delta arithmetic is exact and respects request precision',()=>{
 expect(adjustmentNet('100','2')).toBe('98.000000');expect(adjustmentNet('-0.000003','-0.000001')).toBe('-0.000002');expect(adjustmentNet('2','2')).toBe('0.000000');expect(adjustmentNet('','2')).toBeNull();expect(adjustmentNet('1.0000001','0')).toBeNull();expect(adjustmentNet('99999999999999.999999','-0.000001')).toBeNull();
});
