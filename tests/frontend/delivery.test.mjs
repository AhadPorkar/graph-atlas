import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { parseAssetLines, RELEASE_STAGES } from '../../repository-server/src/main/resources/static/delivery.js';

test('release states are explicit and immutable', () => {
  assert.deepEqual(RELEASE_STAGES, ['DRAFT', 'IN_REVIEW', 'APPROVED', 'RELEASED', 'REJECTED', 'REVOKED']);
  assert.throws(() => RELEASE_STAGES.push('FAKE'), TypeError);
});
test('capture parses multiple formats without rewriting their paths', () => {
  assert.deepEqual(parseAssetLines(' npm-hosted/@team/ui/-/ui-1.tgz\r\n\nraw-hosted/releases/app.zip '), [
    {repo: 'npm-hosted', path: '@team/ui/-/ui-1.tgz'}, {repo: 'raw-hosted', path: 'releases/app.zip'},
  ]);
});
test('empty capture input remains empty so the form can reject it', () => {
  assert.deepEqual(parseAssetLines('  \n\r\n'), []);
});
test('duplicate references cannot silently inflate a snapshot', () => {
  assert.throws(() => parseAssetLines('raw/a\nraw/a'), /duplicateAsset/);
});
test('capture rejects traversal, encoded paths, control characters and missing repositories', () => {
  for (const value of ['x', '/a', 'raw/', 'raw/../a', 'raw/./a', 'raw/a//b', 'raw/%2e%2e/a', 'raw/a\\b', 'raw/a\x00b', 'RAW/a']) {
    assert.throws(() => parseAssetLines(value), /assetReference/, value);
  }
});
test('capsule names are never evaluated as JavaScript or markup by the parser', () => {
  const [asset] = parseAssetLines('raw/<img-onerror=x>');
  assert.equal(asset.path, '<img-onerror=x>'); // HTML escaping is the rendering layer responsibility.
});
test('every static delivery translation exists in all three catalogs', async () => {
  const code = await readFile(new URL('../../repository-server/src/main/resources/static/delivery.js', import.meta.url), 'utf8');
  const keys = [...code.matchAll(/\bt\('([^']+)'/g)].map(match => match[1]);
  for (const language of ['en','de','fa']) {
    const catalog = JSON.parse(await readFile(new URL(`../../repository-server/src/main/resources/static/locales/${language}.json`, import.meta.url), 'utf8'));
    for (const key of [...keys, ...RELEASE_STAGES.map(s => 'stage.' + s)]) assert.ok(catalog[key], `${language}:${key}`);
  }
});
