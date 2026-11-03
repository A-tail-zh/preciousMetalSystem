import {expect,type Locator,type Page} from '@playwright/test'

/** A wrapper div is clickable even when Element Plus has disabled its input. */
export async function selectWarehouseOption(page:Page,field:Locator,name:string){
 const control=field.getByRole('combobox')
 await expect(control).toBeVisible()
 await expect(control).toBeEnabled()
 // The readonly native input can be covered by its label; use its supported keyboard interaction.
 await expect(async()=>{
  await expect(control).toBeEnabled()
  if(await control.getAttribute('aria-expanded')!=='true')await control.press('ArrowDown')
  await expect(control).toHaveAttribute('aria-expanded','true',{timeout:500})
 }).toPass({timeout:5000})
 const id=await control.getAttribute('aria-controls')
 expect(id,'展开后的下拉框应关联选项列表').toBeTruthy()
 const menu=page.locator('[id="'+id+'"]')
 await expect(menu).toBeVisible()
 const option=menu.getByRole('option',{name,exact:true})
 await expect(option).toBeVisible()
 await option.click()
 if(await control.getAttribute('aria-expanded')==='true')await control.press('Escape')
 await expect(control).toHaveAttribute('aria-expanded','false')
}
