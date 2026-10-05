# CI-Korrektur: npm-Pfade mit Scope und CSP-sichere Browsertests

[English](../en/ci-troubleshooting.md) | [Testbericht](testing.md).

## Befund aus den Protokollen

Das hochgeladene Archiv `logs_101023669665.zip` enthält die drei Jobprotokolle
von Lauf `37298864318`, Commit `c25472be9f32415f199cdedd0664105d09bd6676`.
Java 21 und Java 25 konnten beide Module kompilieren und Spring Boot 4.1.1 mit
Tomcat 11.0.24 starten. Im Servermodul scheiterte jeweils einer von 20 Tests:
`TomcatWireTest.streamingRangesScopedPathsAndStatelessAuthentication:74`.
`PUT /repository/npm-hosted/@wire%2Fdemo` erwartete HTTP 201, erhielt jedoch
400 mit `BAD_PATH` und `Request rejected by the HTTP firewall`.
Die vier JUnit-Einstiegstests des Kerns waren erfolgreich. Es handelt sich um
eine Laufzeit-Assertion, nicht um einen Compiler-, Abhängigkeits- oder Startfehler.
Siehe [extrahierte Diagnose](../qa/ci-fix2-diagnosis.txt).

Diese Protokolle gehören zum **unkorrigierten** Commit. Sie belegen weder den
Erfolg dieser Korrektur noch nachfolgender Paketierungs- und Integrationstests.

## Java-Korrektur

In `ServletContainerConfiguration.java` wird
`connector.setEncodedSolidusHandling("passthrough")` ersetzt durch:

```java
connector.setEncodedSolidusHandling("decode");
connector.setEncodedReverseSolidusHandling("reject");
```

Bei `passthrough` bleibt `%2F` im Servlet-Mapping-Pfad erhalten. Die strikte
Spring-Security-Firewall verwirft dort ein verbleibendes `%`, auch wenn kodierte
Schrägstriche erlaubt wurden. Tomcat soll den dekodierten Mapping-Pfad liefern.

`HttpServletRequest.getRequestURI()` bleibt unverändert kodiert.
`RequestAuditFilter` prüft diesen Originalpfad mit `RequestPaths.decode()` vor
den Security-Filterketten. Der bereits dekodierte Servlet-Pfad wird nicht erneut
dekodiert. Beide Ansichten werden unabhängig aus dem Original abgeleitet.
Kodierte Trennzeichen außerhalb des Paketnamensraums, kodierte Repository-Namen,
Traversal, doppelte Schrägstriche, Prozentzeichen und Backslashes bleiben gesperrt.

Weder `setAllowUrlEncodedPercent(true)` noch gelöschte Firewall-Blocklisten,
ausgelassene Tests oder eine entfernte Pfadprüfung sind Teil dieser Korrektur.

## Beibehaltene Browserkorrektur

Das Browserprotokoll zeigt unabhängig davon einen CSP-Fehler in
`Page.wait_for_function`. Die sieben Aufrufstellen wurden durch wiederholende
Locator-Assertions ersetzt:

```python
from playwright.sync_api import expect
expect(page.locator("html")).to_have_attribute("lang", "de")
```

`unsafe-eval` und `bypass_csp` bleiben deaktiviert. CI verlangt weiterhin native
Browsernavigation; der lokale Bridge-Modus ersetzt diesen Test nicht.
Fehler-Screenshots und Traces bleiben verfügbar.

## Regressionstests

`RequestPathPolicyTestMain` ergänzt 30 ausgeführte Prüfungen für Original-URIs:
Scope-Namen, erhaltene Pluszeichen und abgewiesene mehrdeutige Pfade. Die Tests
laufen über `CoreRegressionTest` in Maven und die Core-Skripte für Linux/PowerShell.

`ServletPathFirewallTest` enthält drei neue Maven-Tests für die Firewall-Sichten
und erhaltene Blocklisten. Sie wurden hier geschrieben, aber nicht ausgeführt.
`TomcatWireTest` behält die ursprüngliche Veröffentlichungs-Assertion bei und
prüft weitere Kodierungen, identische Dateiinhalte und abgewiesene Pfade mit
und ohne Zugangsdaten. Nur der echte Maven-Lauf prüft das Tomcat-Verhalten.

## Erneute Ausführung

```bash
mvn -B -ntp -e -Djava.version=21 clean verify
# Mit aktivem JDK 25:
mvn -B -ntp -e -Djava.version=25 clean verify
# Nach erfolgreichem Maven-Build:
python tests/integration.py
python tests/csp_assertions_test.py
python tests/ui_test.py
```

CI speichert Maven-Ausgabe und Surefire-Berichte auch bei einem Fehler.
Die Exit-Codes bleiben trotz `tee` erhalten. Den Lauf des **neuen Commits**
prüfen; ein erneuter Lauf des alten Commits verwendet weiterhin den alten Code.
Weitere Fehler in späteren Schritten sind nicht ausgeschlossen.

## Tatsächliche lokale Validierung

246 Core-Prüfungen auf OpenJDK 21.0.11 (216 bestehende und 30 neue),
31 Frontend-Tests, acht CSP-Tests, 14 Gate-Client-Tests, zwölf Restore-Tests und
vier Evidenz-Verifier-Tests waren erfolgreich. Die native Browsernavigation
wurde von der Umgebung mit `ERR_BLOCKED_BY_ADMINISTRATOR` blockiert.
Maven und JDK 25 sind lokal nicht verfügbar. Der vollständige korrigierte
Spring-Boot-/Tomcat-Lauf sowie GitHub CI sind **noch nicht bestätigt**.
Siehe [aktueller Validierungsbericht](../qa/ci-fix2-validation.json).
Der [frühere CSP-Bericht](../qa/ci-hotfix-validation.json) ist historisch.

## Quellen

- [Tomcat-Connector](https://tomcat.apache.org/tomcat-11.0-doc/config/http.html).
- [Servlet-API](https://tomcat.apache.org/tomcat-11.0-doc/servletapi/jakarta/servlet/http/HttpServletRequest.html).
- [StrictHttpFirewall](https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/web/firewall/StrictHttpFirewall.html).
- [Spring-Security-Implementierung](https://github.com/spring-projects/spring-security/blob/main/web/src/main/java/org/springframework/security/web/firewall/StrictHttpFirewall.java).
- [Playwright Locator-Assertions](https://playwright.dev/python/docs/api/class-locatorassertions).
