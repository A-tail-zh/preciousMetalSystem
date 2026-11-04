import {test,expect} from '@playwright/test'
import {rawReturnAvailable as available,rawReturnError as error} from '../src/utils/raw-return-allowance'
const a={id:'a',batchId:'b',remainingWeight:'280.000000',batchRemainingWeight:'90.000000',returnableWeight:'90.000000'},b={...a,id:'c',remainingWeight:'10.000000',returnableWeight:'10.000000'}
test('shared batch budget, independent source cap, edited row exclusion and deletion',()=>{
 const drafts=[{sourceLineId:'a',netWeight:'30'},{sourceLineId:'c',netWeight:'5'}]
 expect(available(a,[a,b],drafts)).toBe('55.000000');expect(available(b,[a,b],drafts)).toBe('5.000000')
 expect(available(a,[a,b],drafts,0)).toBe('85.000000');expect(available(a,[a,b],drafts.slice(1))).toBe('85.000000')
 expect(error(a,[a,b],drafts,'55')).toBe('');expect(error(a,[a,b],drafts,'55.000001')).toContain('55.000000')
})
test('exact decimals and unrelated batches',()=>{
 const s={...a,remainingWeight:'0.300000',batchRemainingWeight:'0.300000',returnableWeight:'0.300000'}
 expect(available(s,[s],[{sourceLineId:'a',netWeight:'0.100001'}])).toBe('0.199999')
 expect(available(a,[a,{...b,batchId:'other'}],[{sourceLineId:'c',netWeight:'50'}])).toBe('90.000000')
})
test('closed, missing fields and historical negatives fail closed',()=>{
 expect(available({...a,returnableWeight:'0.000000'},[a],[])).toBe('0.000000')
 expect(error({...a,batchRemainingWeight:'-1'},[a],[],'1')).toContain('人工核对')
 expect(error({...a,returnableWeight:undefined},[a],[],'1')).toContain('未加载')
})

test('raw return accepts zero and six-place zero but rejects negative weight',()=>{
 expect(error(a,[a],[],'0')).toBe('');expect(error(a,[a],[],'0.000000')).toBe('');expect(error(a,[a],[],'-0.000001')).not.toBe('');expect(available(a,[a],[{sourceLineId:'a',netWeight:'0'}])).toBe('90.000000')
})

test('zero return never bypasses an explicitly closed source batch',()=>{expect(error({...a,batchStatus:'SETTLED'},[a],[],'0')).not.toBe('');expect(error({...a,batchStatus:'VOID'},[a],[],'0')).not.toBe('')})
