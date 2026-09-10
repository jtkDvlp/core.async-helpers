# core.async-helpers

@../claude-guidelines/CLAUDE.md

<!--
  Der Import oben erwartet das Repo `claude-guidelines` als
  Schwesterverzeichnis neben diesem hier. Eingebunden, nicht kopiert:
  so wirkt jede Änderung an den Richtlinien sofort, und es gibt keinen
  zweiten Stand, der veraltet. Fehlt das Verzeichnis, bleibt die Zeile
  wirkungslos — dann gilt nur, was hier steht.
-->

Die projektübergreifenden Richtlinien oben gelten vollständig. Was hier
folgt, ergänzt sie um das, was nur für dieses Projekt gilt — und benennt
die Stellen, an denen es bewusst abweicht.

## Worum es geht

Eine Bibliothek, die `core.async` um **Fehlerfortpflanzung über den
go-Block-Stapel** erweitert, dazu Interop für Promises und
Callback-APIs. Kein Anwendungsprojekt: es gibt kein `main`, kein `core`
und keine Komponenten.

Die Bibliothek ist `.cljc` und läuft auf beiden Plattformen. Jede
Änderung muss auf Clojure **und** ClojureScript funktionieren.

## Abweichungen von den Richtlinien

**Die Artifact-Group heißt hier `jtk-dvlp`, nicht `jtkdvlp`.** Die
Namespaces (`jtk-dvlp.async`, …) und der Pfad `src/jtk_dvlp/` sind
öffentliche API und stehen seit Version 1.0.0 so. Ein Umbenennen wäre ein
Breaking Change für jeden Nutzer der Bibliothek — der Preis ist höher als
der Gewinn. Neue Namespaces folgen dem **bestehenden** Schema
`jtk-dvlp.async.…`, nicht dem der Richtlinien.

**Es gibt keine Bereichsebene.** Ein Build-Ziel, ein Pfad — die Ebene
träfe keine Trennung. Genau der Fall, den die Richtlinien beschreiben.

## Aufbau

| Verzeichnis | Inhalt |
|---|---|
| `src/jtk_dvlp/` | Die Bibliothek. Alles `.cljc`. |
| `test/jtk_dvlp/` | Tests, spiegeln `src/` wider: zu `x.cljc` gehört `x_test.cljc`. |
| `dev/` | REPL-Einstieg (`user.clj`), figwheel-Ziel (`jtk_dvlp/user.cljs`), Spielwiese (`your_project.cljc`). |
| `target/` | Alles Generierte. Jederzeit löschbar (`lein clean`). |

Die Namespaces:

| Namespace | Thema |
|---|---|
| `jtk-dvlp.async` | Kern: `go`, `<!`, `map`, `reduce` und die übrigen Fortpflanzungs-Varianten. |
| `jtk-dvlp.async.interop.promise` | Brücke zu Promises (JS) bzw. `promise` (JVM). |
| `jtk-dvlp.async.interop.callback` | Brücke zu Callback-basierten APIs. |
| `jtk-dvlp.async.print` | Debug-Ausgabe eines Kanalergebnisses. |
| `jtk-dvlp.async.test-support` | Nur `test/`: `deftest-async`, damit ein Testkörper auf beiden Plattformen gleich aussieht. |

## Das Makro-Muster

Fast alles hier ist ein **Makro**, und Makros gibt es in ClojureScript
nur zur Compile-Zeit in Clojure. Daraus folgt ein Muster, das in jedem
Namespace dieser Bibliothek wiederkehrt und das man kennen muss, bevor
man etwas hinzufügt:

```clojure
(ns jtk-dvlp.async.beispiel
  #?(:cljs
     (:require-macros
      [jtk-dvlp.async.beispiel :refer [mein-makro]]))
  ,,,)

#?(:clj
   (defmacro mein-makro
     [x]
     (if (:ns &env)
       `(cljs.core.async/…)     ; ClojureScript-Zweig
       `(clojure.core.async/…)))) ; Clojure-Zweig
```

Drei Dinge daran sind leicht zu übersehen:

- **`#?(:clj …)` um jedes `defmacro`.** Ohne das versucht der
  ClojureScript-Compiler, das Makro als Funktion zu übersetzen.
- **Der `:require-macros`-Selbstverweis.** Nur so ist das Makro in
  ClojureScript unter dem eigenen Namespace erreichbar. Jedes neue Makro
  gehört dort in die `:refer`-Liste — sonst fehlt es genau auf einer der
  beiden Plattformen, und der Compiler sagt nichts.
- **`(:ns &env)` unterscheidet die Zielplattform**, nicht ein
  Reader-Conditional. Der Reader-Conditional greift beim Übersetzen des
  Makros selbst — der läuft immer in Clojure. Welche Plattform der
  *Aufrufer* hat, steht erst in `&env`.

In expandiertem Code werden Namespaces **voll ausgeschrieben**
(`jtk-dvlp.async/<!`, nicht `<!`). Der Aufrufer hat unsere Aliase nicht.

## WATCHOUT: nicht mit `clojure.core.async` mischen

Die Fehlerfortpflanzung lebt davon, dass eine Ausnahme als *Wert* durch
den Kanal wandert und erst beim Herausnehmen mit unserem `<!` wieder
geworfen wird. Ein `core.async/<!` dazwischen nimmt den Wert stumm
entgegen — der Fehler verschwindet, und niemand merkt es.

Das gilt auch im Testcode. Wo dort trotzdem `core.async/<!!` steht, ist
das Absicht: dann soll der Rohwert geprüft werden, statt ihn geworfen zu
bekommen. Solche Stellen tragen einen Kommentar.

## Tests

```bash
lein test              # Clojure
lein test :known-bug   # nur die Tests zu bekannten Fehlern
lein test :all         # beides
```

Das läuft in der GitHub Action (`.github/workflows/ci.yml`) bei jedem
Push und jedem Pull Request.

Die Tests liegen in `.cljc` und sind so geschrieben, dass sie auf
**beiden** Plattformen laufen. Dafür
gibt es `jtk-dvlp.async.test-support/deftest-async`: der Testkörper läuft
in einem `go`-Block, auf Clojure blockierend abgewartet, auf
ClojureScript über `cljs.test/async`. Ein neuer Test wird damit
geschrieben und ist ohne Zutun auf beiden Plattformen abgedeckt.

Plattformspezifisches (`<!!`, `thread`, `<p!!`) steht in
Reader-Conditionals — dort und nur dort.

**`^:known-bug` markiert Tests, die einen vorhandenen Fehler der
Bibliothek einfordern.** Sie beschreiben das richtige Verhalten und
schlagen deshalb heute fehl; der Test-Selektor nimmt sie aus dem
CI-Lauf. Welcher Fehler gemeint ist, steht als `FIXME:` direkt darüber.
Wer einen davon behebt, entfernt die Markierung mit.

**Ein `deftest-async` ohne `is` schlägt nicht fehl.** Läuft der Körper in
eine Ausnahme, bevor eine Assertion greift, meldet der Test das; läuft er
gar nicht an, meldet niemand etwas. Deshalb prüft jeder Test mindestens
eine Zusicherung.
