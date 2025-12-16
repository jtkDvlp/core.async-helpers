(defproject jtk-dvlp/core.async-helpers "3.4.0"
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
