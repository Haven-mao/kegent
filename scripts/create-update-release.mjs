import { readFileSync, mkdirSync, copyFileSync, writeFileSync, statSync, readdirSync } from 'node:fs';
import { resolve, join, basename } from 'node:path';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

export const COMPATIBLE_CERT = '3b2756ff4b3920fac9738a20d5d2ad6900a4e5c4f774edea62a1ffc049ba8190';
export function createManifest({ badging, certificates, size, sha256, tag, baseUrl, notes = '', allowDebug = false, applicationId = 'com.haven.kegent', expectedCert = COMPATIBLE_CERT, now = new Date() }) {
  const match = badging.match(/^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'/m);
  const sdk = badging.match(/^sdkVersion:'(\d+)'/m);
  if (!match || !sdk) throw new Error('Cannot read APK package/version/minSdk metadata.');
  if (match[1] !== applicationId) throw new Error('APK applicationId does not match the release channel.');
  if (!allowDebug && /^application-debuggable\s*$/m.test(badging)) throw new Error('Refusing a debuggable APK for a public release.');
  const actualCerts = [...new Set([...certificates.matchAll(/^(?:Signer[^:\r\n]*|V\d+ Signer[^:\r\n]*):? certificate SHA-256 digest: ([a-f0-9]{64})\s*$/gmi)].map(m => m[1].toLowerCase()))];
  if (actualCerts.length !== 1 || actualCerts[0] !== expectedCert.toLowerCase()) throw new Error('APK certificate differs from the existing installed release.');
  if (!/^v\d+\.\d+\.\d+$/.test(tag) || `v${match[3].replace(/-dev$/, '')}` !== tag) throw new Error('Tag must match the APK versionName.');
  const url = new URL(baseUrl);
  if (url.protocol !== 'https:' || url.username || url.password || url.hash || url.search) throw new Error('Release base URL must be HTTPS without credentials, query or fragment.');
  if (!Number.isSafeInteger(size) || size <= 0 || size > 250 * 1024 * 1024 || !/^[a-f0-9]{64}$/.test(sha256)) throw new Error('Invalid APK size or SHA-256.');
  if (notes.length > 16000) throw new Error('Release notes exceed 16000 characters.');
  const versionCode = Number(match[2]); if (versionCode < 1 || versionCode > 2147483647) throw new Error('Invalid versionCode.');
  return {
    schemaVersion: 1, applicationId: match[1], channel: 'stable', versionCode, versionName: match[3], minSdk: Number(sdk[1]),
    apkUrl: `${baseUrl.replace(/\/$/, '')}/kegent-${tag.slice(1)}.apk`, sizeBytes: size, sha256,
    releaseNotes: notes, publishedAt: now.toISOString()
  };
}

function main() {
  const options = {};
  for (let i = 2; i < process.argv.length; i++) {
    const flag = process.argv[i];
    if (flag === '--allow-debug') { options.allowDebug = true; continue; }
    if (!['--apk', '--out', '--tag', '--base-url', '--notes-file', '--sdk', '--expected-cert', '--application-id'].includes(flag) || !process.argv[i + 1]) throw new Error(`Unknown or incomplete argument: ${flag}`);
    options[flag.slice(2)] = process.argv[++i];
  }
  for (const name of ['apk', 'out', 'tag', 'base-url']) if (!options[name]) throw new Error(`Missing --${name}`);
  const sdkRoot = options.sdk || process.env.ANDROID_SDK_ROOT || process.env.ANDROID_HOME;
  if (!sdkRoot) throw new Error('Pass --sdk or configure ANDROID_HOME.');
  const buildToolsRoot = join(sdkRoot, 'build-tools');
  const tools = readdirSync(buildToolsRoot).filter(n => /^\d+\.\d+\.\d+$/.test(n)).sort((a, b) => a.localeCompare(b, undefined, { numeric: true })).at(-1);
  if (!tools) throw new Error('Android build tools not found.');
  const bin = join(buildToolsRoot, tools), apk = resolve(options.apk);
  const aapt = join(bin, process.platform === 'win32' ? 'aapt.exe' : 'aapt');
  const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
  const badging = execFileSync(aapt, ['dump', 'badging', apk], { encoding: 'utf8' });
  const certificates = execFileSync(java, ['-jar', join(bin, 'lib', 'apksigner.jar'), 'verify', '--print-certs', apk], { encoding: 'utf8' });
  const manifest = createManifest({ badging, certificates, size: statSync(apk).size,
    sha256: createHash('sha256').update(readFileSync(apk)).digest('hex'), tag: options.tag, baseUrl: options['base-url'],
    notes: options['notes-file'] ? readFileSync(options['notes-file'], 'utf8') : '', allowDebug: options.allowDebug,
    applicationId: options['application-id'], expectedCert: options['expected-cert'] || COMPATIBLE_CERT });
  const output = resolve(options.out); mkdirSync(output, { recursive: true });
  const target = join(output, basename(new URL(manifest.apkUrl).pathname));
  if (target !== apk) copyFileSync(apk, target);
  writeFileSync(join(output, 'update.json'), JSON.stringify(manifest, null, 2) + '\n');
  writeFileSync(join(output, basename(target) + '.sha256'), `${manifest.sha256}  ${basename(target)}\n`);
  process.stdout.write(`Prepared ${basename(target)} and update.json (versionCode ${manifest.versionCode}).\n`);
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { main(); } catch (error) { process.stderr.write(`${error.message}\n`); process.exitCode = 1; }
}
