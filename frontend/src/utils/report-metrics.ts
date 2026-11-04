import {decimalText} from './business-decimal'
export function lossRateText(value:unknown){return value==null||value===''?'—':decimalText(value)+'%'}
export function thresholdText(row:Record<string,any>){if(row.thresholdValue!=null)return decimalText(row.thresholdValue);if(row.thresholdMin!=null&&row.thresholdMax!=null)return decimalText(row.thresholdMin)+'～'+decimalText(row.thresholdMax);return '—'}
