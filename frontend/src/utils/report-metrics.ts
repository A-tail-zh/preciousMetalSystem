export function lossRateText(value:unknown){return value==null||value===''?'—':String(value)+'%'}
export function thresholdText(row:Record<string,any>){if(row.thresholdValue!=null)return String(row.thresholdValue);if(row.thresholdMin!=null&&row.thresholdMax!=null)return row.thresholdMin+'～'+row.thresholdMax;return '—'}
