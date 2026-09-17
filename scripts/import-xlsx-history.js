#!/usr/bin/env node
'use strict';

/**
 * One-time backfill: reads a hand-maintained budget spreadsheet
 * (compte_test.xlsx, one tab per month, six side-by-side transaction blocks
 * per tab) and imports the target months' rows into LeBonCompte.
 *
 * Reuses the existing backend instead of adding a migration endpoint:
 *   1. Builds a synthetic OFX file from the parsed rows and POSTs it to
 *      /api/imports (the real import pipeline: ACCTID account matching,
 *      FITID dedup, POINTEE status).
 *   2. Backfills category on the created transactions via PUT
 *      /api/transactions/{id} (status is left untouched by that endpoint).
 *
 * No xlsx library: an .xlsx is a zip of XML, unzipped by hand below.
 *
 * Usage:
 *   node scripts/import-xlsx-history.js [--dry-run] [--file path/to.xlsx] [--base-url http://localhost:8080/api]
 */

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const args = process.argv.slice(2);
const DRY_RUN = args.includes('--dry-run');
const fileArgIdx = args.indexOf('--file');
const XLSX_PATH = fileArgIdx >= 0 ? args[fileArgIdx + 1] : path.join(__dirname, '..', 'compte_test.xlsx');
const baseUrlArgIdx = args.indexOf('--base-url');
const BASE_URL = baseUrlArgIdx >= 0 ? args[baseUrlArgIdx + 1] : 'http://localhost:8080/api';

// Only these 9 tabs are in scope; map tab name -> {year, month}.
const TARGET_MONTHS = {
  jan2026: { year: 2026, month: 1 },
  'fév2026': { year: 2026, month: 2 },
  mars2026: { year: 2026, month: 3 },
  avril2026: { year: 2026, month: 4 },
  mai2026: { year: 2026, month: 5 },
  juin2026: { year: 2026, month: 6 },
  juillet2026: { year: 2026, month: 7 },
  aout2026: { year: 2026, month: 8 },
  sept2026: { year: 2026, month: 9 },
};

// [Montant | Libellé | Catégorie | "x"] per block; block 2 has no category column.
const BLOCKS = [
  { name: 'block1', type: 'REVENU', amount: 'C', label: 'D', category: 'E' },
  { name: 'block2', type: 'DEPENSE', amount: 'H', label: 'I', category: null },
  { name: 'block3', type: 'DEPENSE', amount: 'L', label: 'M', category: 'N' },
  { name: 'block4', type: 'DEPENSE', amount: 'Q', label: 'R', category: 'S' },
  { name: 'block5', type: 'DEPENSE', amount: 'V', label: 'W', category: 'X' },
  { name: 'block6', type: 'DEPENSE', amount: 'AA', label: 'AB', category: 'AC' },
];

// ---------- minimal zip reader ----------

function unzip(buffer, wantedNames) {
  const wanted = new Set(wantedNames);
  const eocdSig = 0x06054b50;
  let eocdOffset = -1;
  for (let i = buffer.length - 22; i >= 0; i--) {
    if (buffer.readUInt32LE(i) === eocdSig) {
      eocdOffset = i;
      break;
    }
  }
  if (eocdOffset < 0) throw new Error('Not a zip file (no EOCD found): ' + XLSX_PATH);

  const centralDirOffset = buffer.readUInt32LE(eocdOffset + 16);
  const entryCount = buffer.readUInt16LE(eocdOffset + 10);

  const result = {};
  let off = centralDirOffset;
  for (let i = 0; i < entryCount; i++) {
    const sig = buffer.readUInt32LE(off);
    if (sig !== 0x02014b50) throw new Error('Bad central directory entry at ' + off);
    const compressionMethod = buffer.readUInt16LE(off + 10);
    const compressedSize = buffer.readUInt32LE(off + 20);
    const fileNameLength = buffer.readUInt16LE(off + 28);
    const extraLength = buffer.readUInt16LE(off + 30);
    const commentLength = buffer.readUInt16LE(off + 32);
    const localHeaderOffset = buffer.readUInt32LE(off + 42);
    const fileName = buffer.toString('utf8', off + 46, off + 46 + fileNameLength);

    if (wanted.has(fileName)) {
      const lh = localHeaderOffset;
      const lhSig = buffer.readUInt32LE(lh);
      if (lhSig !== 0x04034b50) throw new Error('Bad local file header for ' + fileName);
      const lhFileNameLength = buffer.readUInt16LE(lh + 26);
      const lhExtraLength = buffer.readUInt16LE(lh + 28);
      const dataStart = lh + 30 + lhFileNameLength + lhExtraLength;
      const compressed = buffer.subarray(dataStart, dataStart + compressedSize);
      result[fileName] = compressionMethod === 0 ? Buffer.from(compressed) : zlib.inflateRawSync(compressed);
    }

    off += 46 + fileNameLength + extraLength + commentLength;
  }
  return result;
}

// ---------- xlsx XML parsing ----------

function decodeXmlEntities(s) {
  return s
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&amp;/g, '&');
}

function escapeXml(s) {
  return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function parseSharedStrings(xml) {
  const strings = [];
  const siRegex = /<si>([\s\S]*?)<\/si>/g;
  let m;
  while ((m = siRegex.exec(xml))) {
    const tRegex = /<t[^>]*>([\s\S]*?)<\/t>/g;
    let t,
      text = '';
    while ((t = tRegex.exec(m[1]))) text += t[1];
    strings.push(decodeXmlEntities(text));
  }
  return strings;
}

function colToIndex(colLetters) {
  let idx = 0;
  for (const ch of colLetters) idx = idx * 26 + (ch.charCodeAt(0) - 64);
  return idx - 1;
}

function parseSheetRows(xml, sharedStrings) {
  const rows = [];
  const rowRegex = /<row[^>]*r="(\d+)"[^>]*>([\s\S]*?)<\/row>/g;
  let rm;
  while ((rm = rowRegex.exec(xml))) {
    const rowNum = parseInt(rm[1], 10);
    const rowXml = rm[2];
    const cellRegex = /<c r="([A-Z]+)(\d+)"([^>]*)>([\s\S]*?)<\/c>/g;
    const cells = {};
    let cm;
    while ((cm = cellRegex.exec(rowXml))) {
      const colLetters = cm[1];
      const attrs = cm[3];
      const inner = cm[4];
      const type = (attrs.match(/t="([^"]+)"/) || [])[1] || 'n';
      const v = (inner.match(/<v>([\s\S]*?)<\/v>/) || [])[1];
      if (v === undefined) continue;
      let display;
      if (type === 's') display = sharedStrings[parseInt(v, 10)];
      else if (type === 'str' || type === 'inlineStr') display = decodeXmlEntities(v);
      else display = v; // number
      cells[colLetters] = display;
    }
    rows[rowNum] = cells;
  }
  return rows;
}

// ---------- row -> transaction extraction ----------

function isBlankOrNumericOnly(text) {
  if (text === undefined || text === null) return true;
  const t = String(text).trim();
  return t === '' || /^\d+([.,]\d+)?$/.test(t);
}

function extractRows(tabName, rows) {
  const { year, month } = TARGET_MONTHS[tabName];
  const date = `${year}-${String(month).padStart(2, '0')}-01`;
  const out = [];
  const skipped = [];

  for (let r = 2; r < rows.length; r++) {
    const cells = rows[r];
    if (!cells) continue;
    for (const block of BLOCKS) {
      const rawAmount = cells[block.amount];
      const rawLabel = cells[block.label];
      const rawCategory = block.category ? cells[block.category] : undefined;

      const amount = rawAmount === undefined ? NaN : parseFloat(String(rawAmount).replace(',', '.'));
      const label = rawLabel === undefined ? '' : String(rawLabel).trim();

      if (!Number.isFinite(amount) || amount === 0 || label === '') {
        if (rawAmount !== undefined || rawLabel !== undefined) {
          skipped.push({ tab: tabName, row: r, block: block.name, reason: !label ? 'no label' : 'no/zero amount' });
        }
        continue;
      }

      const category = isBlankOrNumericOnly(rawCategory) ? null : String(rawCategory).trim();
      const signedAmount = block.type === 'DEPENSE' ? -Math.abs(amount) : Math.abs(amount);

      out.push({
        tab: tabName,
        block: block.name,
        row: r,
        date,
        label,
        amount: signedAmount,
        type: block.type,
        category,
        fitid: `XLSX-${tabName}-${block.name}-${r}`,
      });
    }
  }
  return { rows: out, skipped };
}

// ---------- OFX generation ----------

function buildOfx(accountNumber, transactions) {
  const stmttrns = transactions
    .map((t) => {
      const dtposted = t.date.replace(/-/g, '');
      const trnamt = t.amount.toFixed(2);
      const trntype = t.amount < 0 ? 'DEBIT' : 'CREDIT';
      return `<STMTTRN><TRNTYPE>${trntype}<DTPOSTED>${dtposted}<TRNAMT>${trnamt}<FITID>${escapeXml(t.fitid)}<NAME>${escapeXml(t.label)}</STMTTRN>`;
    })
    .join('');

  return (
    'OFXHEADER:100\r\n' +
    'DATA:OFXSGML\r\n' +
    'VERSION:102\r\n' +
    'SECURITY:NONE\r\n' +
    'ENCODING:USASCII\r\n' +
    'CHARSET:1252\r\n' +
    'COMPRESSION:NONE\r\n' +
    'OLDFILEUID:NONE\r\n' +
    'NEWFILEUID:NONE\r\n' +
    '\r\n' +
    '<OFX><SIGNONMSGSRSV1><SONRS><STATUS><CODE>0<SEVERITY>INFO</STATUS>' +
    '<DTSERVER>20260101000000<LANGUAGE>FRE</SONRS></SIGNONMSGSRSV1>' +
    '<BANKMSGSRSV1><STMTTRNRS><TRNUID>1<STATUS><CODE>0<SEVERITY>INFO</STATUS>' +
    '<CLTCOOKIE>1<STMTRS><CURDEF>EUR<BANKACCTFROM><BANKID>0<BRANCHID>0' +
    `<ACCTID>${escapeXml(accountNumber)}<ACCTTYPE>CHECKING<ACCTKEY>0</BANKACCTFROM>` +
    '<BANKTRANLIST><DTSTART>20260101<DTEND>20260930' +
    stmttrns +
    '</BANKTRANLIST><LEDGERBAL><BALAMT>0.00<DTASOF>20260930</LEDGERBAL>' +
    '</STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>'
  );
}

function toWindows1252(str) {
  const bad = [];
  for (const ch of str) {
    if (ch.charCodeAt(0) > 255) bad.push(ch);
  }
  if (bad.length > 0) {
    console.warn(`WARNING: ${bad.length} character(s) outside Latin-1 range found, they will be mangled:`, [...new Set(bad)].join(' '));
  }
  return Buffer.from(str, 'latin1');
}

// ---------- HTTP helpers ----------

async function apiGet(pathname) {
  const res = await fetch(BASE_URL + pathname);
  if (!res.ok) throw new Error(`GET ${pathname} -> ${res.status}: ${await res.text()}`);
  return res.json();
}

async function apiPostJson(pathname, body) {
  const res = await fetch(BASE_URL + pathname, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`POST ${pathname} -> ${res.status}: ${await res.text()}`);
  return res.json();
}

async function apiPutJson(pathname, body) {
  const res = await fetch(BASE_URL + pathname, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`PUT ${pathname} -> ${res.status}: ${await res.text()}`);
  return res.json();
}

async function apiPostOfx(ofxBytes, filename) {
  const form = new FormData();
  form.append('file', new Blob([ofxBytes], { type: 'application/x-ofx' }), filename);
  const res = await fetch(BASE_URL + '/imports', { method: 'POST', body: form });
  if (!res.ok) throw new Error(`POST /imports -> ${res.status}: ${await res.text()}`);
  return res.json();
}

// ---------- main ----------

async function main() {
  console.log(`Reading ${XLSX_PATH}...`);
  const buffer = fs.readFileSync(XLSX_PATH);

  const workbookXmlName = 'xl/workbook.xml';
  const relsName = 'xl/_rels/workbook.xml.rels';
  const sharedStringsName = 'xl/sharedStrings.xml';
  const entries = unzip(buffer, [workbookXmlName, relsName, sharedStringsName]);

  const workbookXml = entries[workbookXmlName].toString('utf8');
  const relsXml = entries[relsName].toString('utf8');
  const sharedStrings = parseSharedStrings(entries[sharedStringsName].toString('utf8'));

  // tab name -> r:id
  const sheetRid = {};
  const sheetTagRe = /<sheet name="([^"]*)"[^>]*r:id="(rId\d+)"/g;
  let sm;
  while ((sm = sheetTagRe.exec(workbookXml))) {
    sheetRid[decodeXmlEntities(sm[1])] = sm[2];
  }

  // r:id -> worksheets/sheetN.xml
  const ridTarget = {};
  const relRe = /<Relationship Id="(rId\d+)"[^>]*Target="([^"]*)"/g;
  let rr;
  while ((rr = relRe.exec(relsXml))) {
    ridTarget[rr[1]] = rr[2];
  }

  const targetSheetFiles = {};
  for (const tabName of Object.keys(TARGET_MONTHS)) {
    const rid = sheetRid[tabName];
    if (!rid) throw new Error(`Tab not found in workbook: ${tabName}`);
    targetSheetFiles[tabName] = 'xl/' + ridTarget[rid];
  }

  const sheetEntries = unzip(buffer, Object.values(targetSheetFiles));

  let allRows = [];
  let allSkipped = [];
  for (const [tabName, file] of Object.entries(targetSheetFiles)) {
    const xml = sheetEntries[file].toString('utf8');
    const rows = parseSheetRows(xml, sharedStrings);
    const { rows: extracted, skipped } = extractRows(tabName, rows);
    console.log(`${tabName}: ${extracted.length} row(s) to import, ${skipped.length} skipped`);
    allRows = allRows.concat(extracted);
    allSkipped = allSkipped.concat(skipped);
  }

  console.log(`\nTotal: ${allRows.length} row(s) across ${Object.keys(TARGET_MONTHS).length} months, ${allSkipped.length} skipped.`);
  if (allSkipped.length > 0) {
    console.log('Skipped rows (review these manually):');
    for (const s of allSkipped) console.log(`  ${s.tab} row ${s.row} ${s.block}: ${s.reason}`);
  }

  if (DRY_RUN) {
    console.log('\n--dry-run: not calling the API. Sample of first 10 rows:');
    for (const r of allRows.slice(0, 10)) {
      console.log(`  ${r.date} | ${r.type.padEnd(7)} | ${r.amount.toFixed(2).padStart(10)} | ${r.category ?? '(none)'} | ${r.label}`);
    }
    return;
  }

  console.log('\nResolving account...');
  const accounts = await apiGet('/accounts');
  const account = accounts.find((a) => a.name === 'Compte courant') ?? accounts.find((a) => a.isDefault);
  if (!account) throw new Error('No "Compte courant" / default account found.');
  if (!account.accountNumber) throw new Error(`Account "${account.name}" has no accountNumber set — set one first (Comptes page) so OFX import can match it.`);
  console.log(`Using account "${account.name}" (accountNumber=${account.accountNumber}).`);

  console.log('\nBuilding synthetic OFX and importing...');
  const ofxText = buildOfx(account.accountNumber, allRows);
  const ofxBytes = toWindows1252(ofxText);
  const importResult = await apiPostOfx(ofxBytes, 'xlsx-history.ofx');
  console.log('Import result:', importResult);

  console.log('\nResolving categories...');
  const categories = await apiGet('/categories');
  const categoryByName = new Map(categories.map((c) => [c.name.trim().toLowerCase(), c.id]));
  let categoriesCreated = 0;
  for (const r of allRows) {
    if (!r.category) continue;
    const key = r.category.toLowerCase();
    if (!categoryByName.has(key)) {
      const created = await apiPostJson('/categories', { name: r.category, parentId: null });
      categoryByName.set(key, created.id);
      categoriesCreated++;
      console.log(`  created category "${r.category}" (id ${created.id})`);
    }
  }

  console.log('\nBackfilling categories on imported transactions...');
  let assigned = 0;
  let notMatched = 0;
  for (const tabName of Object.keys(TARGET_MONTHS)) {
    const { year, month } = TARGET_MONTHS[tabName];
    const monthParam = `${year}-${String(month).padStart(2, '0')}`;
    const monthTransactions = await apiGet(`/transactions?month=${monthParam}`);
    const rowsForMonth = allRows.filter((r) => r.date.startsWith(monthParam));

    for (const r of rowsForMonth) {
      if (!r.category) continue;
      const match = monthTransactions.find(
        (t) => t.date === r.date && t.label === r.label && Math.abs(t.amount - r.amount) < 0.005 && t.categoryId === null,
      );
      if (!match) {
        notMatched++;
        continue;
      }
      const categoryId = categoryByName.get(r.category.toLowerCase());
      await apiPutJson(`/transactions/${match.id}`, {
        date: match.date,
        label: match.label,
        amount: Math.abs(match.amount),
        accountId: match.accountId,
        categoryId,
        type: match.type,
      });
      assigned++;
    }
  }

  console.log(`\nDone. Categories created: ${categoriesCreated}. Category assignments applied: ${assigned}. Not matched for category backfill: ${notMatched}.`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
