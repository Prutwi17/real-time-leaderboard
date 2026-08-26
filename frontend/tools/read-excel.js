const XLSX = require('xlsx');
const path = process.argv[2];
const wb = XLSX.readFile(path);
console.log('Sheet names:', wb.SheetNames);
wb.SheetNames.forEach(name => {
  const ws = wb.Sheets[name];
  const data = XLSX.utils.sheet_to_json(ws, {defval:''});
  console.log('\n=== SHEET:', name, '===');
  console.log('Rows:', data.length);
  if (data.length > 0) {
    console.log('Headers:', Object.keys(data[0]));
    console.log('First 3 rows:');
    data.slice(0, 3).forEach((r, i) => console.log(`  [${i}]:`, JSON.stringify(r)));
    console.log('Last row:', JSON.stringify(data[data.length-1]));
  }
});
