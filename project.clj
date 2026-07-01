(defproject jtk-dvlp/core.async-helpers "4.0.0-SNAPSHOT"
  :description
  "Helper pack for core.async"

  :url
  "https://github.com/jtkDvlp/core.async-helpers"

  :license
  {:name
   "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"

   :url
   "https://www.eclipse.org/legal/epl-2.0/"}

  :plugins
  [[lein-ancient "0.7.0"]]

  :source-paths
  ["src"]

  :target-path
  "target"

  :clean-targets
  ^{:protect false}
  [:target-path]

  :dependencies
  [[org.clojure/core.async "1.9.865"]]

  :profiles
  {:provided
   {:dependencies
    [[org.clojure/clojure "1.12.5"]]}

   :dev
   {:dependencies
    [[com.bhauman/figwheel-main "0.2.20"]]

    :source-paths
    ["dev"]}

   :repl
   {:dependencies
    [[cider/piggieback "0.7.0"]]

    :repl-options
    {:nrepl-middleware
     [cider.piggieback/wrap-cljs-repl]

     :init-ns
     user}}

   ,,,})
