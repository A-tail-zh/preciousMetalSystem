import type { FormRules } from 'element-plus'
import { fieldRule, requiredRule, textRules } from '@/utils/input-validation'

export const customerFields = [
  { key: 'name', label: '客户名称', max: 100 },
  { key: 'companyName', label: '客户公司名称', max: 100 },
  { key: 'address', label: '客户地址', max: 500 },
  { key: 'contactName', label: '联络人名称', max: 100 },
  { key: 'contactPhone', label: '联络人电话', max: 11 },
  { key: 'department', label: '部门', max: 100 }
] as const
export function customerRules(): FormRules {
  const rules: FormRules = Object.fromEntries(customerFields.map(f => [f.key, textRules(f.label, f.max, f.key !== 'department')]))
  rules.factoryId = [requiredRule('所属工厂', true, true)]
  rules.contactPhone = [...textRules('联络人电话', 11, true), fieldRule(value => /^1[3-9]\d{9}$/.test(String(value ?? '').trim()) ? '' : '联络人电话须为11位大陆手机号')]
  return rules
}
