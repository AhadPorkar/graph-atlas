package ir.graph.repo.core.i18n;

import java.util.List;
import java.util.Locale;

/**
 * Small, framework-independent Accept-Language policy.
 * English is always the fallback, including when the server's own locale is German or Persian.
 */
public final class SupportedLanguages {
    public static final Locale DEFAULT = Locale.ENGLISH;
    public static final List<Locale> SUPPORTED = List.of(Locale.ENGLISH, Locale.GERMAN, Locale.forLanguageTag("fa"));
    public static final List<String> TAGS = SUPPORTED.stream().map(Locale::toLanguageTag).toList();

    private SupportedLanguages() { }

    public static Locale resolve(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank() || acceptLanguage.length() > 4096) {
            return DEFAULT;
        }
        try {
            var ranges = Locale.LanguageRange.parse(acceptLanguage).stream()
                    .filter(range -> range.getWeight() > 0).toList();
            Locale match = Locale.lookup(ranges, SUPPORTED);
            return match == null ? DEFAULT : match;
        } catch (IllegalArgumentException exception) {
            return DEFAULT;
        }
    }
}
