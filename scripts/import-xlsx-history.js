#!/usr/bin/env node
'use strict';

/**
 * One-time backfill: reads a hand-maintained budget spreadsheet
 * (compte_test.xlsx, one tab per month) and imports the target months' rows
 * into LeBonCompte.
 *
 * Each monthly tab has TWO stacked sections of 6 side-by-side transaction
 * blocks, sharing the same column layout (block starts at A, F, J, O, T, Y):
 *   - Section 1 (row 1 titles): CREDIT, LOGEMENT, ALIMENTATION, ENFANTS,
 *     ADULTES, SANTE.
 *   - Section 2 (titles on the row where column A says "EPARGNE", found
 *     dynamically since its row number varies by month): EPARGNE,
 *     ABONNEMENTS, TRANSPORT, LOISIRS ET VIE SOCIALE, VACANCES, DIVERS.
 * Each block is [Montant | Libellé | Catégorie | "x"] (the block starting at
 * column F never has a Catégorie column, in either section). The block's
 * title is the PARENT category; the per-row Catégorie cell is the CHILD.
 * Only the CREDIT block is REVENU; every other block (including EPARGNE) is
 * DEPENSE. The "x" checkbox is ignored entirely.
 *
 * Reuses the existing backend instead of adding a migration endpoint:
 *   1. Builds a synthetic OFX file from the parsed rows and POSTs it to
 *      /api/imports (the real import pipeline: ACCTID account matching,
 *      FITID dedup, POINTEE status).
 *   2. Backfills category (creating parent then child as needed) on the
 *      created transactions via PUT /api/transactions/{id}, which leaves
 *      status untouched.
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

// Every block starts at one of these columns, in both sections. The block
// starting at F never has a Catégorie column (checkbox sits right after the
// label instead) — confirmed structurally identical in both sections.
const BLOCK_STARTS = [
  { start: 'A', hasCategory: true },
  { start: 'F', hasCategory: false },
  { start: 'J', hasCategory: true },
  { start: 'O', hasCategory: true },
  { start: 'T', hasCategory: true },
  { start: 'Y', hasCategory: true },
];

const TITLE_DISPLAY_NAMES = {
  CREDIT: 'Crédit',
  LOGEMENT: 'Logement',
  ALIMENTATION: 'Alimentation',
  ENFANTS: 'Enfants',
  ADULTES: 'Adultes',
  SANTE: 'Santé',
  EPARGNE: 'Épargne',
  TRANSPORT: 'Transport',
  'LOISIRS ET VIE SOCIALE': 'Loisirs et vie sociale',
  VACANCES: 'Vacances',
  DIVERS: 'Divers',
};

function displayTitle(raw) {
  const trimmed = raw.trim();
  const key = trimmed.toUpperCase();
  return TITLE_DISPLAY_NAMES[key] ?? trimmed.charAt(0).toUpperCase() + trimmed.slice(1).toLowerCase();
}

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

function indexToCol(idx) {
  let n = idx + 1;
  let s = '';
  while (n > 0) {
    const rem = (n - 1) % 26;
    s = String.fromCharCode(65 + rem) + s;
    n = Math.floor((n - 1) / 26);
  }
  return s;
}

function colOffset(colLetters, offset) {
  return indexToCol(colToIndex(colLetters) + offset);
}

// A self-closing `<c .../>` cell must NOT be treated as an open `<c ...>...</c>`
// cell whose content run all the way to the next real closing tag — that was
// the actual bug behind rows silently vanishing (e.g. "alimentation Sam"):
// a naive `<c ...>([\s\S]*?)<\/c>` regex matches straight through a
// self-closing cell's trailing "/>" as if it were content, swallowing every
// subsequent empty cell and misattributing the NEXT real cell's value to the
// wrong column.
const CELL_RE = /<c r="([A-Z]+)(\d+)"([^>]*?)(?:\/>|>([\s\S]*?)<\/c>)/g;

function parseSheetRows(xml, sharedStrings) {
  const rows = [];
  const rowRegex = /<row[^>]*r="(\d+)"[^>]*>([\s\S]*?)<\/row>/g;
  let rm;
  while ((rm = rowRegex.exec(xml))) {
    const rowNum = parseInt(rm[1], 10);
    const rowXml = rm[2];
    const cells = {};
    let cm;
    CELL_RE.lastIndex = 0;
    while ((cm = CELL_RE.exec(rowXml))) {
      const colLetters = cm[1];
      const attrs = cm[3];
      const inner = cm[4];
      if (inner === undefined) continue; // self-closing: genuinely empty cell
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

function readTitles(cells) {
  return BLOCK_STARTS.map((b) => {
    const raw = cells[b.start];
    return raw === undefined ? null : String(raw).trim();
  });
}

function findSection2Start(rows) {
  for (let r = 2; r < rows.length; r++) {
    const cells = rows[r];
    if (cells && typeof cells['A'] === 'string' && cells['A'].trim().toUpperCase() === 'EPARGNE') {
      return r;
    }
  }
  return null;
}

function extractSection(tabName, rows, titles, fromRow, toRowExclusive, date, out, skipped) {
  for (let r = fromRow; r < toRowExclusive; r++) {
    const cells = rows[r];
    if (!cells) continue;
    for (let i = 0; i < BLOCK_STARTS.length; i++) {
      const { start, hasCategory } = BLOCK_STARTS[i];
      const title = titles[i];
      if (!title) continue; // no title read for this block in this section: skip defensively

      const amountCol = start;
      const labelCol = colOffset(start, 1);
      const categoryCol = hasCategory ? colOffset(start, 2) : null;

      const rawAmount = cells[amountCol];
      const rawLabel = cells[labelCol];
      const rawCategory = categoryCol ? cells[categoryCol] : undefined;

      const amount = rawAmount === undefined ? NaN : parseFloat(String(rawAmount).replace(',', '.'));
      const label = rawLabel === undefined ? '' : String(rawLabel).trim();

      if (!Number.isFinite(amount) || amount === 0 || label === '') {
        if (rawAmount !== undefined || rawLabel !== undefined) {
          skipped.push({ tab: tabName, row: r, block: title, reason: !label ? 'no label' : 'no/zero amount' });
        }
        continue;
      }

      const type = title.toUpperCase() === 'CREDIT' ? 'REVENU' : 'DEPENSE';
      const parentCategory = displayTitle(title);
      const childCategory = isBlankOrNumericOnly(rawCategory) ? null : String(rawCategory).trim();
      const signedAmount = type === 'DEPENSE' ? -Math.abs(amount) : Math.abs(amount);

      out.push({
        tab: tabName,
        block: title,
        row: r,
        date,
        label,
        amount: signedAmount,
        type,
        parentCategory,
        childCategory,
        fitid: `XLSX-${tabName}-${start}-${r}`,
      });
    }
  }
}

function extractRows(tabName, rows) {
  const { year, month } = TARGET_MONTHS[tabName];
  const date = `${year}-${String(month).padStart(2, '0')}-01`;
  const out = [];
  const skipped = [];

  const section1Titles = readTitles(rows[1] || {});
  const section2Start = findSection2Start(rows);
  if (section2Start === null) {
    throw new Error(`${tabName}: could not find the "EPARGNE" section header row.`);
  }
  const section2Titles = readTitles(rows[section2Start] || {});

  extractSection(tabName, rows, section1Titles, 2, section2Start, date, out, skipped);
  extractSection(tabName, rows, section2Titles, section2Start + 1, rows.length, date, out, skipped);

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

// ---------- category resolution (parent then child, created lazily) ----------

class CategoryResolver {
  constructor(existingCategories) {
    this.byId = new Map(existingCategories.map((c) => [c.id, c]));
    this.parentKey = new Map(); // name(lowercase) -> id, for top-level categories only
    for (const c of existingCategories) {
      if (c.parentId === null) this.parentKey.set(c.name.trim().toLowerCase(), c.id);
    }
    this.childKey = new Map(); // `${parentId}::${name(lowercase)}` -> id
    for (const c of existingCategories) {
      if (c.parentId !== null) this.childKey.set(`${c.parentId}::${c.name.trim().toLowerCase()}`, c.id);
    }
    this.created = 0;
  }

  async resolveParent(name) {
    const key = name.trim().toLowerCase();
    if (this.parentKey.has(key)) return this.parentKey.get(key);
    const created = await apiPostJson('/categories', { name, parentId: null });
    this.parentKey.set(key, created.id);
    this.created++;
    console.log(`  created parent category "${name}" (id ${created.id})`);
    return created.id;
  }

  async resolveChild(parentId, name) {
    const key = `${parentId}::${name.trim().toLowerCase()}`;
    if (this.childKey.has(key)) return this.childKey.get(key);
    const created = await apiPostJson('/categories', { name, parentId });
    this.childKey.set(key, created.id);
    this.created++;
    console.log(`  created child category "${name}" (id ${created.id}, parent ${parentId})`);
    return created.id;
  }

  /** Rows with no sub-category get the parent itself as their category, so every row ends up categorized. */
  async resolve(parentName, childName) {
    const parentId = await this.resolveParent(parentName);
    if (!childName) return parentId;
    return this.resolveChild(parentId, childName);
  }
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
    for (const s of allSkipped) console.log(`  ${s.tab} row ${s.row} [${s.block}]: ${s.reason}`);
  }

  if (DRY_RUN) {
    console.log('\n--dry-run: not calling the API. Sample of first 15 rows:');
    for (const r of allRows.slice(0, 15)) {
      const cat = r.childCategory ? `${r.parentCategory} > ${r.childCategory}` : `${r.parentCategory} (no sub-category)`;
      console.log(`  ${r.date} | ${r.type.padEnd(7)} | ${r.amount.toFixed(2).padStart(10)} | ${cat.padEnd(35)} | ${r.label}`);
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

  console.log('\nResolving categories (parent then child)...');
  const existingCategories = await apiGet('/categories');
  const resolver = new CategoryResolver(existingCategories);
  const categoryIdByRow = new Map(); // row identity -> categoryId
  for (const r of allRows) {
    const categoryId = await resolver.resolve(r.parentCategory, r.childCategory);
    categoryIdByRow.set(r, categoryId);
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
      const categoryId = categoryIdByRow.get(r);
      if (!categoryId) continue;
      const match = monthTransactions.find(
        (t) => t.date === r.date && t.label === r.label && Math.abs(t.amount - r.amount) < 0.005 && t.categoryId === null,
      );
      if (!match) {
        notMatched++;
        continue;
      }
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

  console.log(`\nDone. Categories created: ${resolver.created}. Category assignments applied: ${assigned}. Not matched for category backfill: ${notMatched}.`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
