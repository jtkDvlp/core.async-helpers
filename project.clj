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
  [[org.clojure/clojure "1.11.3"]
   [org.clojure/clojurescript "1.11.132"]
   [org.clojure/core.async "1.3.610"]]

  ;; NOTE: The tests are `.cljc` on purpose — they are meant to run
  ;;       against ClojureScript later on, unchanged.
  :test-paths
  ["test"]

  ;; NOTE: `:known-bug` marks tests that demand behaviour the library
  ;;       does not have yet. They spell out what is correct and
  ;;       therefore fail today — they stay out of CI and run any time
  ;;       with `lein test :known-bug`. Which bug is meant stands as a
  ;;       FIXME right at the test.
  :test-selectors
  {:default
   (complement :known-bug)

   :known-bug
   :known-bug

   :all
   (constantly true)}

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
