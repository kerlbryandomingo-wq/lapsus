// Scans build output for things that must never ship: API keys, private keys, tokens,
// source maps, and environment files. Usage: node scripts/check-artifacts.mjs [dir ...]
import { readFileSync, readdirSync, statSync, existsSync } from 'node:fs';
import { join, extname, basename } from 'node:path';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const asar = require('@electron/asar');

const PATTERNS = [
  ['Anthropic API key', /sk-ant-[A-Za-z0-9_-]{20,}/],
  ['OpenAI API key', /sk-(?:proj-)?[A-Za-z0-9_-]{32,}/],
  ['GitHub token', /gh[pousr]_[A-Za-z0-9]{36,}/],
  ['AWS access key', /AKIA[0-9A-Z]{16}/],
  ['Private key', /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/],
  ['Slack token', /xox[baprs]-[A-Za-z0-9-]{10,}/],
];
const FORBIDDEN_NAMES = [/\.map$/, /^\.env/, /\.pem$/, /\.pfx$/, /\.p12$/, /secrets\.json$/];
const TEXT_EXT = new Set([
  '.js',
  '.cjs',
  '.mjs',
  '.json',
  '.html',
  '.css',
  '.txt',
  '.yml',
  '.yaml',
  '.md',
  '',
]);

const findings = [];
let scanned = 0;

function checkText(label, text) {
  scanned++;
  for (const [name, re] of PATTERNS)
    if (re.test(text)) findings.push(`${label}: looks like a ${name}`);
}
function checkName(label) {
  const name = basename(label);
  if (FORBIDDEN_NAMES.some((re) => re.test(name)))
    findings.push(`${label}: file type must not ship`);
}
function scanAsar(file) {
  for (const entry of asar.listPackage(file)) {
    const inner = entry.replace(/^[\\/]/, '');
    const label = `${file}:${inner}`;
    checkName(label);
    let stat;
    try {
      stat = asar.statFile(file, inner);
    } catch {
      continue;
    }
    if (!stat || stat.files || !TEXT_EXT.has(extname(inner))) continue;
    checkText(label, asar.extractFile(file, inner).toString('utf8'));
  }
}
function scan(path) {
  const st = statSync(path);
  if (st.isDirectory()) {
    for (const name of readdirSync(path)) scan(join(path, name));
    return;
  }
  checkName(path);
  if (path.endsWith('.asar')) return scanAsar(path);
  if (TEXT_EXT.has(extname(path)) && st.size < 20 * 1024 * 1024)
    checkText(path, readFileSync(path, 'utf8'));
}

const targets = process.argv.slice(2);
if (!targets.length) targets.push('out');
for (const t of targets) {
  if (!existsSync(t)) {
    console.error(`Not found: ${t}`);
    process.exit(2);
  }
  scan(t);
}
if (findings.length) {
  console.error(
    `Artifact check FAILED (${findings.length}):\n` + findings.map((f) => `  - ${f}`).join('\n'),
  );
  process.exit(1);
}
console.log(
  `Artifact check passed: ${scanned} text files scanned, no secrets, source maps or key files found.`,
);
