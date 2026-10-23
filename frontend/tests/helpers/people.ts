import type {Page,Locator} from '@playwright/test'
export async function selectPerson(page:Page,dialog:Locator,label:string,fixture:any){
 const field=dialog.locator('.el-form-item').filter({has:page.locator('label').filter({hasText:new RegExp('^'+label+'$')})});await field.locator('.el-select__wrapper').first().click();
 const id=await field.getByRole('combobox').first().getAttribute('aria-controls');await page.locator('[id="'+id+'"]').getByRole('option',{name:fixture.prefix+'角色A｜'+fixture.prefix+'负责人A｜'+fixture.phoneA,exact:true}).click();await page.keyboard.press('Escape');
}

export function personLabelFor(fixture:any){return fixture.prefix+'角色A｜'+fixture.prefix+'负责人A｜'+fixture.phoneA}
