type CsvRow = Record<string, unknown>;

const csvCell = (value: unknown) => {
  if (value === null || value === undefined) return '';
  const text = typeof value === 'object' ? JSON.stringify(value) : String(value);
  return `"${text.replace(/"/g, '""')}"`;
};

const toCsv = (rows: CsvRow[]) => {
  if (!rows.length) return '';
  const headers = Array.from(rows.reduce((set, row) => {
    Object.keys(row).forEach((key) => set.add(key));
    return set;
  }, new Set<string>()));
  const lines = [
    headers.map(csvCell).join(','),
    ...rows.map((row) => headers.map((key) => csvCell(row[key])).join(',')),
  ];
  return lines.join('\r\n');
};

export const downloadCsv = (fileName: string, rows: CsvRow[]) => {
  const csv = toCsv(rows);
  const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8;' });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = fileName.endsWith('.csv') ? fileName : `${fileName}.csv`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(link.href);
};

export const flattenSectionsForCsv = (sections: Array<{ title: string; rows: CsvRow[] }>) => sections.flatMap((section) => {
  if (!section.rows?.length) return [];
  return section.rows.map((row) => ({
    section: section.title,
    ...row,
  }));
});

const htmlCell = (value: unknown, tag = 'td') => {
  if (value === null || value === undefined) return `<${tag}></${tag}>`;
  const text = typeof value === 'object' ? JSON.stringify(value) : String(value);
  return `<${tag}>${text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')}</${tag}>`;
};

export const downloadExcel = (fileName: string, rows: CsvRow[]) => {
  const headers = rows.length
    ? Array.from(rows.reduce((set, row) => {
      Object.keys(row).forEach((key) => set.add(key));
      return set;
    }, new Set<string>()))
    : [];
  const table = [
    '<table border="1">',
    `<thead><tr>${headers.map((key) => htmlCell(key, 'th')).join('')}</tr></thead>`,
    `<tbody>${rows.map((row) => `<tr>${headers.map((key) => htmlCell(row[key])).join('')}</tr>`).join('')}</tbody>`,
    '</table>',
  ].join('');
  const html = `<html><head><meta charset="utf-8"></head><body>${table}</body></html>`;
  const blob = new Blob([html], { type: 'application/vnd.ms-excel;charset=utf-8;' });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = fileName.endsWith('.xls') ? fileName : `${fileName}.xls`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(link.href);
};
