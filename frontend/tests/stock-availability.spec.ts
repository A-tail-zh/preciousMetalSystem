import{test,expect}from'@playwright/test';
import{stockAvailabilityError as check}from'../src/utils/stock-availability';
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
