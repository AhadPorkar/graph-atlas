/**
 * Runtime localization with an explicit English default.
 *
 * Never infer the first language from the browser or operating system.
 * Only an explicit ?lang= selection or a previously stored choice overrides English.
 * Package coordinates, code examples, hashes and API field names are not translated.
 */
export const DEFAULT_LANGUAGE = 'en';
export const STORAGE_KEY = 'graph.repository.language';
export const LANGUAGES = Object.freeze({
  en: { name: 'English', locale: 'en-US', direction: 'ltr' },
  de: { name: 'Deutsch', locale: 'de-DE', direction: 'ltr' },
  fa: { name: '\u0641\u0627\u0631\u0633\u06cc', locale: 'fa-IR', direction: 'rtl' },
});

export function normalizeLanguage(value) {
  if (typeof value !== 'string') return null;
  const code = value.trim().toLowerCase().split(/[-_]/)[0];
  return Object.hasOwn(LANGUAGES, code) ? code : null;
}

export function initialLanguage(search = '', storage = null) {
  const requested = normalizeLanguage(new URLSearchParams(search).get('lang'));
  if (requested) return requested;
  try {
    return normalizeLanguage(storage?.getItem(STORAGE_KEY)) ?? DEFAULT_LANGUAGE;
  } catch {
    return DEFAULT_LANGUAGE;
  }
}

export function interpolate(message, values = {}) {
  return String(message).replace(/\{([a-zA-Z0-9_]+)\}/g, (match, key) =>
    Object.hasOwn(values, key) ? String(values[key]) : match);
}

export class I18n {
  #catalogs = new Map();
  #loader;
  #storage;
  #request = 0;

  constructor({ loader, storage = null, catalogs = {} } = {}) {
    this.language = DEFAULT_LANGUAGE;
    this.#loader = loader ?? (async language => {
      const response = await fetch(`/locales/${language}.json`, { credentials: 'same-origin' });
      if (!response.ok) throw new Error(`Translation catalog unavailable: ${language}`);
      return response.json();
    });
    this.#storage = storage;
    for (const [language, messages] of Object.entries(catalogs)) {
      this.#catalogs.set(language, Object.freeze({ ...messages }));
    }
  }

  async #load(language) {
    if (!this.#catalogs.has(language)) {
      const messages = await this.#loader(language);
      if (!messages || Array.isArray(messages) || typeof messages !== 'object' ||
          !Object.values(messages).every(value => typeof value === 'string')) {
        throw new TypeError(`Invalid translation catalog: ${language}`);
      }
      this.#catalogs.set(language, Object.freeze({ ...messages }));
    }
  }

  async initialize(language = DEFAULT_LANGUAGE) {
    await this.#load(DEFAULT_LANGUAGE);
    try {
      await this.setLanguage(language, { persist: false });
    } catch {
      this.language = DEFAULT_LANGUAGE;
    }
    return this;
  }

  async setLanguage(language, { persist = true } = {}) {
    const next = normalizeLanguage(language) ?? DEFAULT_LANGUAGE;
    const request = ++this.#request;
    await this.#load(DEFAULT_LANGUAGE);
    await this.#load(next);
    // A slower catalog request must not overwrite a more recent choice.
    if (request !== this.#request) return false;
    this.language = next;
    if (persist) {
      try { this.#storage?.setItem(STORAGE_KEY, next); } catch { /* Storage is optional. */ }
    }
    return true;
  }

  get direction() { return LANGUAGES[this.language].direction; }
  get locale() { return LANGUAGES[this.language].locale; }

  t(key, values = {}) {
    const message = this.#catalogs.get(this.language)?.[key] ??
      this.#catalogs.get(DEFAULT_LANGUAGE)?.[key] ?? key;
    return interpolate(message, values);
  }

  plural(key, count) {
    const category = new Intl.PluralRules(this.locale).select(count);
    const candidate = `${key}.${category}`;
    const available = this.#catalogs.get(this.language) ?? {};
    return this.t(Object.hasOwn(available, candidate) ? candidate : `${key}.other`,
      { count: this.number(count) });
  }

  number(value, options = {}) {
    const numeric = Number(value);
    return new Intl.NumberFormat(this.locale, options).format(Number.isFinite(numeric) ? numeric : 0);
  }

  date(value) {
    if (!value) return this.t('never');
    const date = new Date(value);
    if (!Number.isFinite(date.getTime())) return '\u2014';
    return new Intl.DateTimeFormat(this.locale, {
      dateStyle: 'medium', timeStyle: 'short',
    }).format(date);
  }

  size(value) {
    let bytes = Math.max(0, Number(value) || 0);
    let unit = 0;
    while (bytes >= 1024 && unit < 4) { bytes /= 1024; unit += 1; }
    return `${this.number(bytes, { maximumFractionDigits: 1 })} ${['B', 'KiB', 'MiB', 'GiB', 'TiB'][unit]}`;
  }

  applyDocument(document) {
    document.documentElement.lang = this.language;
    document.documentElement.dir = this.direction;
    document.title = this.t('appName');
    for (const element of document.querySelectorAll('[data-i18n]')) {
      element.textContent = this.t(element.dataset.i18n);
    }
    for (const element of document.querySelectorAll('[data-i18n-aria]')) {
      element.setAttribute('aria-label', this.t(element.dataset.i18nAria));
    }
    for (const element of document.querySelectorAll('[data-language-switch]')) {
      element.value = this.language;
    }
  }
}
