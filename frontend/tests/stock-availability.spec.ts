import{test,expect}from'@playwright/test';
import{stockAvailabilityError as check,releasedTransferError,externalEntryError}from'../src/utils/stock-availability';
test('stock checks include pending duplicate lines, ignore the edited row and accept the exact decimal boundary',()=>{
 const stock={category:'RAW',netWeight:'0.300000',quantity:0};const first={inventoryId:'1',netWeight:'0.100000'};
 expect(check(stock,{inventoryId:'1',netWeight:'0.200000'},[first])).toBe('');
 expect(check(stock,{inventoryId:'1',netWeight:'0.200001'},[first])).toContain('库存不足');
 expect(check(stock,{inventoryId:'1',netWeight:'0.300000'},[first],0)).toBe('');
 expect(check(stock,{inventoryId:'2',netWeight:'0.300000'},[first])).toBe('');
 expect(check({category:'RAW',netWeight:'99999999999999.999999',quantity:0},{inventoryId:'1',netWeight:'99999999999999.999999'},[])).toBe('');
});
test('product stock checks weight and pieces independently, while raw stock ignores piece balances',()=>{
 const stock={category:'SEMI',netWeight:'10',quantity:5};
 expect(check(stock,{inventoryId:'1',netWeight:'1',quantity:6},[])).toContain('数量');
 expect(check(stock,{inventoryId:'1',netWeight:'11',quantity:1},[])).toContain('净重');
 expect(check(stock,{inventoryId:'1',netWeight:'2',quantity:3},[{inventoryId:'1',netWeight:'2',quantity:3}])).toContain('数量');
 expect(check({...stock,category:'RAW',quantity:0},{inventoryId:'1',netWeight:'10',quantity:0},[])).toBe('');
});

test('released transfer rejects orphan pieces or weight using exact aggregate decimals',()=>{const b={quantity:4,netWeight:'40.000000'};expect(releasedTransferError(b,[{quantity:4,netWeight:'39'}])).toContain('同时');expect(releasedTransferError(b,[{quantity:3,netWeight:'40'}])).toContain('同时');expect(releasedTransferError(b,[{quantity:1,netWeight:'5'},{quantity:3,netWeight:'35'}])).toBe('');expect(releasedTransferError(b,[{quantity:2,netWeight:'19'}])).toBe('');expect(releasedTransferError(b,[{quantity:2,netWeight:'20'},{quantity:2,netWeight:'19'}])).toContain('同时');expect(releasedTransferError({quantity:2,netWeight:'0.300000'},[{quantity:1,netWeight:'0.100000'},{quantity:1,netWeight:'0.200000'}])).toBe('');expect(releasedTransferError({quantity:2,netWeight:'0.300000'},[{quantity:2,netWeight:'0.299999'}])).toContain('同时')})

test('external entry capacity sums all external pools but excludes raw and own semi',()=>{expect(externalEntryError('4',[{releaseId:'A',quantity:2},{releaseId:'B',quantity:2}])).toBe('');expect(externalEntryError('4',[{releaseId:'A',quantity:4},{releaseId:'B',quantity:1}])).toContain('共享');expect(externalEntryError('4',[{releaseId:'A',quantity:3},{releaseId:'A',quantity:2}])).toContain('5');expect(externalEntryError('4',[{quantity:999},{releaseId:null,quantity:999},{releaseId:'A',quantity:4}])).toBe('');expect(externalEntryError('0',[{releaseId:'A',quantity:1}])).toContain('共享');expect(externalEntryError('0',[{quantity:10}])).toBe('');expect(externalEntryError('1000000',[{releaseId:'A',quantity:500000},{releaseId:'B',quantity:500000}])).toBe('');expect(externalEntryError('1000000',[{releaseId:'A',quantity:500000},{releaseId:'B',quantity:500001}])).toContain('共享')})
