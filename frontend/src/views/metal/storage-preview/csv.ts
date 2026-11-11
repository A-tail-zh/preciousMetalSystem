export function parseCsv(text: string): string[][] {
  text=text.replace(/^\uFEFF/,'')
  const rows:string[][]=[];let row:string[]=[],field='',quoted=false,closed=false
  for(let i=0;i<text.length;i++){
    const c=text[i]
    if(quoted){if(c==='"'){if(text[i+1]==='"'){field+='"';i++}else{quoted=false;closed=true}}else field+=c;continue}
    if(c==='"'){if(field||closed)throw new Error(`第 ${rows.length+1} 行引号格式错误`);quoted=true;continue}
    if(c===','||c==='\n'||c==='\r'){
      row.push(field);field='';closed=false
      if(c!==','){if(c==='\r'&&text[i+1]==='\n')i++;rows.push(row);row=[]}continue
    }
    if(closed)throw new Error(`第 ${rows.length+1} 行引号后存在无效字符`)
    field+=c
  }
  if(quoted)throw new Error('CSV 引号未闭合')
  if(field||closed||row.length)rows.push([...row,field])
  return rows.filter(r=>r.some(v=>v!==''))
}
export function csvText(headers:string[],rows:(string|number)[][]) {
  const encode=(value:string|number)=>{let s=String(value??'');if(/^[\s]*[=+\-@]/.test(s))s="'"+s;return '"'+s.replaceAll('"','""')+'"'}
  return '\uFEFF'+[headers,...rows].map(r=>r.map(encode).join(',')).join('\r\n')
}
export function downloadCsv(filename:string,headers:string[],rows:(string|number)[][]){
  const url=URL.createObjectURL(new Blob([csvText(headers,rows)],{type:'text/csv;charset=utf-8'})),link=document.createElement('a')
  link.href=url;link.download=filename.endsWith('.csv')?filename:filename+'.csv';link.click();setTimeout(()=>URL.revokeObjectURL(url),1000)
}
