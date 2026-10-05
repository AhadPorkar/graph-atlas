package ir.graph.repo.core;

import ir.graph.repo.core.i18n.SupportedLanguages;
import java.util.Locale;

public final class LocalizationTestMain {
    private static int checks;
    private static void expect(String input, String expected) {
        String actual = SupportedLanguages.resolve(input).toLanguageTag();
        if (!actual.equals(expected)) throw new AssertionError(input + " -> " + actual + ", expected " + expected);
        checks++;
    }
    public static void main(String[] args) {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("fa-IR"));
            expect(null, "en"); expect("", "en"); expect(" ", "en");
            expect("en", "en"); expect("en-GB", "en"); expect("en-US", "en");
            expect("de", "de"); expect("de-DE", "de"); expect("de-AT", "de"); expect("DE-de", "de");
            expect("fa", "fa"); expect("fa-IR", "fa"); expect("fa-AF", "fa");
            expect("fr-FR", "en"); expect("*", "en");
            expect("fr-FR,de-DE;q=0.9,en;q=0.5", "de");
            expect("de;q=0.1,fa;q=0.9,en;q=0.5", "fa");
            expect("de;q=0,en;q=1", "en"); expect("de;q=0,fa;q=0.8", "fa");
            expect("de;q=invalid", "en"); expect("de;q=2", "en");
            expect("../../de", "en"); expect("x".repeat(4097), "en");
            if (!SupportedLanguages.TAGS.equals(java.util.List.of("en", "de", "fa"))) throw new AssertionError();
            checks++;
            System.out.println("Localization policy: " + checks + " checks passed.");
        } finally { Locale.setDefault(previous); }
    }
}
