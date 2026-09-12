(defproject jtk-dvlp/core.async-helpers "3.6.1" ;; x-release-please-version
  :description
  "Helper pack for core.async"

  :url
  "https://github.com/jtkDvlp/core.async-helpers"

  :license
  {:name
   "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"

   :url
   "https://www.eclipse.org/legal/epl-2.0/"}

  ;; NOTE: `lein ancient` lists outdated dependencies. Declared here so
  ;;       the answer does not depend on what happens to be installed
  ;;       on whoever asks.
  :plugins
  [[lein-ancient "1.0.0"]]

  :source-paths
  ["src"]

  :target-path
  "target"

  :clean-targets
  ^{:protect false}
  [:target-path]

  ;; NOTE: The credentials come from the environment, not from a file
  ;;       — `:env/clojars_username` reads `CLOJARS_USERNAME`,
  ;;       `:env/clojars_password` reads `CLOJARS_PASSWORD`. In CI those
  ;;       hold a Clojars deploy token and the matching user name, taken
  ;;       from the repository secrets. Locally they are unset, and lein
  ;;       asks as it always did.
  ;;
  ;;       `:sign-releases false` because lein otherwise insists on a
  ;;       GPG signature and CI has no key. Clojars does not require
  ;;       one. Turning it back on needs a key in the run, not just the
  ;;       flag flipped.
  :deploy-repositories
  [["clojars"
    {:url
     "https://repo.clojars.org/"

     :username
     :env/clojars_username

     :password
     :env/clojars_password

     :sign-releases
     false}]]

  :dependencies
  [[org.clojure/clojure "1.12.6"]
   [org.clojure/clojurescript "1.12.145"]
   [org.clojure/core.async "1.9.865"]]

  ;; NOTE: The tests are `.cljc` on purpose — they are meant to run
  ;;       against ClojureScript later on, unchanged.
  :test-paths
  ["test"]

  ;; NOTE: `:known-bug` marks tests that demand behaviour the library
  ;;       does not have yet. They spell out what is correct and
  ;;       therefore fail — they stay out of CI and run any time with
  ;;       `lein test :known-bug`. Which bug is meant stands as a FIXME
  ;;       right at the test.
  ;;
  ;;       There are none at the moment. The selector stays anyway: it
  ;;       is the way to keep a bug that turns up but is not fixed in
  ;;       the same breath.
  :test-selectors
  {:default
   (complement :known-bug)

   :known-bug
   :known-bug

   :all
   (constantly true)}

  ;; NOTE: Compiles the same `.cljc` tests for Node. Running them is a
  ;;       separate `node target/test-cljs/tests.js` — lein does not
  ;;       take that step, it stays with the caller (see the README and
  ;;       the CI workflow).
  :aliases
  {"test-cljs"
   ["run" "-m" "cljs.main"
    "--target" "node"
    "--output-dir" "target/test-cljs"
    "--output-to" "target/test-cljs/tests.js"
    "--compile" "jtk-dvlp.test-runner"]}

  :profiles
  {:dev
   {:dependencies
    [[com.bhauman/figwheel-main "0.2.20"]]

    :source-paths
    ["dev"]}

   :repl
   {:dependencies
    ;; WATCHOUT: piggieback and figwheel have to match. A mismatch does
    ;;           not fail the build — it shows up as a cljs REPL that
    ;;           misbehaves, which no test here can catch. 0.7.0 exists;
    ;;           this pair is the one known to work.
    [[cider/piggieback "0.6.1"]]

    :repl-options
    {:nrepl-middleware
     [cider.piggieback/wrap-cljs-repl]

     :init-ns
     user}}

   ,,,})
