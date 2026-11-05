import {expect,type Page} from '@playwright/test'

export async function filterFactory(page:Page,name:string){
 const filters=page.getByRole('search',{name:'筛选条件'}),control=filters.getByRole('combobox',{name:'工厂',exact:true})
 const opened=page.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/metal/filter-options/factories'))
 await control.click()
 await opened
 await expect(page.getByRole('listbox',{name:'工厂',exact:true}).getByRole('option').first()).toBeVisible()
 await control.fill(name)
 await page.getByRole('option',{name,exact:true}).click()
 await filters.getByRole('button',{name:'查询',exact:true}).click()
 await expect(filters.getByText(name,{exact:true})).toBeVisible()
}
