type MeasurementRow={category?:string;measurementMode?:string;effectiveMeasurementMode?:string}
/** RAW's historical persisted mode is not its stock contract; use the server's derived field. */
export function effectiveMeasurementMode(row:MeasurementRow|null|undefined):'PIECE'|'WEIGHT'{
 if(row?.category==='RAW')return row.effectiveMeasurementMode==='PIECE'?'PIECE':'WEIGHT'
 return (row?.effectiveMeasurementMode??row?.measurementMode)==='WEIGHT'?'WEIGHT':'PIECE'
}
export function managesPieces(row:MeasurementRow|null|undefined){return effectiveMeasurementMode(row)==='PIECE'}
