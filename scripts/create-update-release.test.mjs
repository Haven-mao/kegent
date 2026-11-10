import test from 'node:test';
import assert from 'node:assert/strict';
import { createManifest, COMPATIBLE_CERT } from './create-update-release.mjs';
const fixture = {
  badging: "package: name='com.haven.kegent' versionCode='11' versionName='1.4.4'\nsdkVersion:'26'\n",
  certificates: `Signer #1 certificate SHA-256 digest: ${COMPATIBLE_CERT}\n`,
  size: 12345, sha256: 'a'.repeat(64), tag: 'v1.4.4', baseUrl: 'https://github.com/Haven-mao/kegent/releases/download/v1.4.4', notes: '更新说明'
};
test('manifest is generated from the inspected artifact', () => {
  const m = createManifest(fixture); assert.equal(m.versionCode, 11); assert.equal(m.minSdk, 26);
  assert.equal(m.apkUrl, fixture.baseUrl + '/kegent-1.4.4.apk'); assert.equal(m.releaseNotes, '更新说明');
});
test('wrong signing certificate cannot be released', () => assert.throws(() => createManifest({ ...fixture, certificates: `Signer #1 certificate SHA-256 digest: ${'b'.repeat(64)}` }), /certificate/));
test('build tools 37 signer output and matching multiple schemes are supported', () => {
  assert.equal(createManifest({ ...fixture, certificates: `V2 Signer: certificate SHA-256 digest: ${COMPATIBLE_CERT}\nV3 Signer: certificate SHA-256 digest: ${COMPATIBLE_CERT}\n` }).versionCode, 11);
});
test('wrong package and mismatched tags are rejected', () => {
  assert.throws(() => createManifest({ ...fixture, badging: fixture.badging.replace('com.haven.kegent', 'com.other.app') }), /applicationId/);
  assert.throws(() => createManifest({ ...fixture, tag: 'v1.4.5' }), /Tag/);
});
test('debuggable APK requires an explicit local-preview override', () => {
  const badging = fixture.badging + 'application-debuggable\n';
  assert.throws(() => createManifest({ ...fixture, badging }), /debuggable/);
  assert.equal(createManifest({ ...fixture, badging, allowDebug: true }).versionCode, 11);
});
test('download URLs and artifact sizes are validated', () => {
  assert.throws(() => createManifest({ ...fixture, baseUrl: 'http://example.com/files' }), /HTTPS/);
  assert.throws(() => createManifest({ ...fixture, size: 0 }), /size/);
});
