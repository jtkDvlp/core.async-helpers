(defproject jtk-dvlp/core.async-helpers "1.0.0"
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

  :profiles
  {:provided
   {:dependencies
    [[org.clojure/clojure "1.10.0"]
     [org.clojure/clojurescript "1.10.773"]
     [org.clojure/core.async "1.2.603"]]}

   :dev
   {:dependencies
    [[com.bhauman/figwheel-main "0.2.7"]]

    :source-paths
    ["dev"]}

   :repl
   {:dependencies
    [[cider/piggieback "0.5.0"]]

    :repl-options
    {:nrepl-middleware
     [cider.piggieback/wrap-cljs-repl]

     :init-ns
     user}}

   ,,,})
