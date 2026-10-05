import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { I18n, DEFAULT_LANGUAGE, STORAGE_KEY, normalizeLanguage, initialLanguage, interpolate } from '../../repository-server/src/main/resources/static/i18n.js';
import { clientExample } from '../../repository-server/src/main/resources/static/client-examples.js';

const catalogs = Object.fromEntries(await Promise.all(['en', 'de', 'fa'].map(async language => [
  language, JSON.parse(await readFile(new URL(`../../repository-server/src/main/resources/static/locales/${language}.json`, import.meta.url), 'utf8')),
])));
const translator = async language => {
  const i18n = new I18n({ catalogs });
  await i18n.initialize(language);
  return i18n;
};

test('English is the explicit first-run default', () => {
  assert.equal(DEFAULT_LANGUAGE, 'en');
  assert.equal(initialLanguage('', null), 'en');
});
test('browser regional locales normalize only when explicitly selected', () => {
  assert.equal(normalizeLanguage('de-DE'), 'de');
  assert.equal(normalizeLanguage('FA_ir'), 'fa');
  assert.equal(normalizeLanguage('fr'), null);
  assert.equal(normalizeLanguage('__proto__'), null);
});
test('explicit query language wins over stored language', () => {
  assert.equal(initialLanguage('?lang=en', { getItem: () => 'de' }), 'en');
});
test('invalid query falls back to a valid saved preference', () => {
  assert.equal(initialLanguage('?lang=fr', { getItem: () => 'de' }), 'de');
});
test('invalid persisted values cannot select a non-existent locale', () => {
  assert.equal(initialLanguage('', { getItem: () => 'invalid' }), 'en');
});
test('denied localStorage is safe', () => {
  assert.equal(initialLanguage('', { getItem() { throw new Error('denied'); } }), 'en');
});
test('catalogs have identical keys and matching interpolation placeholders', () => {
  const keys = Object.keys(catalogs.en).sort();
  for (const language of ['de', 'fa']) {
    assert.deepEqual(Object.keys(catalogs[language]).sort(), keys);
    for (const key of keys) {
      const placeholders = value => [...value.matchAll(/\{(\w+)\}/g)].map(match => match[1]).sort();
      assert.deepEqual(placeholders(catalogs[language][key]), placeholders(catalogs.en[key]), `${language}:${key}`);
      assert.ok(catalogs[language][key].trim(), `${language}:${key}`);
    }
  }
});
test('English and German catalogs contain no Persian UI remnants', () => {
  for (const language of ['en', 'de']) {
    assert.ok(!/[\u0600-\u06ff]/u.test(JSON.stringify(catalogs[language])));
  }
});
test('selected language is persisted without storing credentials', async () => {
  const writes = [];
  const i18n = new I18n({ catalogs, storage: { setItem: (...args) => writes.push(args) } });
  await i18n.setLanguage('de');
  assert.deepEqual(writes, [[STORAGE_KEY, 'de']]);
});
test('blocked storage does not prevent changing language', async () => {
  const i18n = new I18n({ catalogs, storage: { setItem() { throw new Error(); } } });
  await i18n.setLanguage('fa');
  assert.equal(i18n.language, 'fa');
});
test('English and German are LTR; Persian is RTL', async () => {
  for (const [language, direction] of [['en', 'ltr'], ['de', 'ltr'], ['fa', 'rtl']]) {
    assert.equal((await translator(language)).direction, direction);
  }
});
test('regional number formatting is applied', async () => {
  assert.equal((await translator('en')).number(1234.5), '1,234.5');
  assert.equal((await translator('de')).number(1234.5), '1.234,5');
  assert.match((await translator('fa')).number(1234.5), /[۰-۹]/u);
});
test('dates are localized; malformed dates cannot break a page', async () => {
  const i18n = await translator('de');
  assert.equal(i18n.date('invalid'), '—');
  assert.equal(i18n.date(''), 'Nie');
  assert.match(i18n.date('2026-10-05T12:30:00Z'), /2026/);
});
test('size units stay technically recognizable in all languages', async () => {
  for (const language of ['en', 'de', 'fa']) {
    const i18n = await translator(language);
    assert.match(i18n.size(1536), /KiB$/);
    assert.match(i18n.size(-1), /B$/);
  }
});
test('singular and plural labels use Intl.PluralRules', async () => {
  const en = await translator('en');
  assert.equal(en.plural('repositoryCount', 1), '1 repository');
  assert.equal(en.plural('repositoryCount', 2), '2 repositories');
  const de = await translator('de');
  assert.equal(de.plural('fileCount', 1), '1 Datei');
  assert.equal(de.plural('fileCount', 2), '2 Dateien');
});
test('missing selected-locale keys fall back to English', async () => {
  const i18n = new I18n({ catalogs: { en: { greeting: 'Hello' }, de: {} } });
  await i18n.initialize('de');
  assert.equal(i18n.t('greeting'), 'Hello');
  assert.equal(i18n.t('unknown'), 'unknown');
});
test('interpolation never reads inherited object properties', () => {
  assert.equal(interpolate('{count} {toString}', { count: 3 }), '3 {toString}');
});
test('failed lazy catalog load retains the previous language', async () => {
  const i18n = new I18n({ catalogs: { en: catalogs.en }, loader: async () => { throw new Error('offline'); } });
  await assert.rejects(i18n.setLanguage('de'));
  assert.equal(i18n.language, 'en');
});
test('initial selected-catalog failure still permits the English UI', async () => {
  const i18n = new I18n({ catalogs: { en: catalogs.en }, loader: async () => { throw new Error(); } });
  await i18n.initialize('fa');
  assert.equal(i18n.language, 'en');
});
test('a slower selection cannot overwrite the most recent selection', async () => {
  let resolveGerman;
  const i18n = new I18n({
    catalogs: { en: catalogs.en, fa: catalogs.fa },
    loader: () => new Promise(resolve => { resolveGerman = resolve; }),
  });
  const german = i18n.setLanguage('de');
  await new Promise(resolve => setImmediate(resolve));
  await i18n.setLanguage('fa');
  resolveGerman(catalogs.de);
  await german;
  assert.equal(i18n.language, 'fa');
});
test('malformed catalog data is rejected', async () => {
  const i18n = new I18n({ catalogs: { en: catalogs.en }, loader: async () => ({ invalid: 42 }) });
  await assert.rejects(i18n.setLanguage('de'), TypeError);
});
test('client examples never publish to proxy or group repositories', async () => {
  const i18n = await translator('de');
  for (const format of ['npm', 'nuget', 'pypi', 'maven', 'raw']) {
    for (const type of ['proxy', 'group']) {
      const result = clientExample({ name: 'test', format, type, url: 'https://repo.example.test/repository/test/' }, 'ci-user', key => i18n.t(key), 'de');
      assert.doesNotMatch(result.code, /npm publish|nuget push|twine upload|deploy:deploy-file|--upload-file/);
      assert.equal(result.hosted, false);
      assert.equal(result.docs, 'docs/de/clients.md');
    }
  }
});
test('hosted examples retain token environment references in all languages', async () => {
  for (const language of ['en', 'de', 'fa']) {
    const i18n = await translator(language);
    for (const format of ['npm', 'nuget', 'pypi', 'maven', 'raw', 'docker']) {
      const example = clientExample({ name: 'hosted', format, type: 'hosted', url: 'https://repo.example.test/repository/hosted/' }, 'ci-user', key => i18n.t(key), language);
      assert.match(example.code, /GR_TOKEN/);
      assert.match(example.code, /repo\.example\.test/);
    }
  }
});
test('shell and XML values in generated examples are escaped', async () => {
  const i18n = await translator('en');
  const maven = clientExample({ name: 'test', format: 'maven', type: 'hosted', url: 'https://repo.example.test/' }, '<ci&user>', key => i18n.t(key));
  assert.match(maven.code, /&lt;ci&amp;user&gt;/);
  const nuget = clientExample({ name: 'test', format: 'nuget', type: 'hosted', url: 'https://repo.example.test/' }, "ci'user", key => i18n.t(key));
  assert.match(nuget.code, /'ci'"'"'user'/);
});
