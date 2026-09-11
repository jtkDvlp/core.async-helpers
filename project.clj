(defproject jtk-dvlp/core.async-helpers "3.6.1"
  :description
  "Helper pack for core.async"

  :url
  "https://github.com/jtkDvlp/core.async-helpers"

  :license
  {:name
   "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"

   :url
   "https://www.eclipse.org/legal/epl-2.0/"}

  :source-paths
  ["src"]

  :target-path
  "target"

  :clean-targets
  ^{:protect false}
  [:target-path]

  :dependencies
  [[org.clojure/clojure "1.11.3"]
   [org.clojure/clojurescript "1.11.132"]
   [org.clojure/core.async "1.3.610"]]

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
    [[com.bhauman/figwheel-main "0.2.18"]]

    :source-paths
    ["dev"]}

   :repl
   {:dependencies
    [[cider/piggieback "0.5.3"]]

    :repl-options
    {:nrepl-middleware
     [cider.piggieback/wrap-cljs-repl]

     :init-ns
     user}}

   ,,,})
