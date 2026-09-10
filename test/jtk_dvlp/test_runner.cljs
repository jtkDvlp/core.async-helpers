(ns jtk-dvlp.test-runner
  "Entry point for the ClojureScript test run under Node.

   The tests themselves are `.cljc` and the very same ones that run on
   the JVM. What lives here is only the scaffolding around them: which
   namespaces take part, which tests stay out, and how the outcome
   reaches the caller as an exit code.

   NOTE: `cljs.test` has no test selectors the way Leiningen does. So
   that `^:known-bug` means the same thing on both platforms, this
   runner gathers the test vars itself and sifts out the marked ones,
   rather than letting `run-tests` loose on the namespaces."

  (:require
   [cljs.test :as test]

   [jtk-dvlp.async-test]
   [jtk-dvlp.async.interop.callback-test]
   [jtk-dvlp.async.interop.promise-test]
   [jtk-dvlp.async.print-test]))


(defn- runnable-vars
  "From a namespace's interns, the vars that really are tests and are
   not marked as a known bug."
  [interns]
  (->> interns
       (vals)
       (filter (comp :test meta))
       (remove (comp :known-bug meta))
       (sort-by (comp str :name meta))))

(def ^:private test-vars
  ;; NOTE: `ns-interns` is a macro and needs the symbol literally —
  ;;       which is why every namespace stands here on its own instead
  ;;       of in a list one could walk. A new test namespace also
  ;;       belongs in the `:require` above.
  (mapcat
   runnable-vars
   [(ns-interns 'jtk-dvlp.async-test)
    (ns-interns 'jtk-dvlp.async.interop.callback-test)
    (ns-interns 'jtk-dvlp.async.interop.promise-test)
    (ns-interns 'jtk-dvlp.async.print-test)]))

(defmethod test/report [::test/default :end-run-tests]
  [summary]
  ;; WATCHOUT: Without this the Node process ends with exit code 0 even
  ;;           on red tests — the CI job would be green when it is not.
  ;;           Because the tests are asynchronous, `run-tests` returns
  ;;           nothing usable; the outcome is only available here.
  (when-not (test/successful? summary)
    (set! (.-exitCode js/process) 1)))

(defn- run-tests!
  []
  (test/set-env! (test/empty-env))
  (test/run-block
   (concat
    (test/test-vars-block test-vars)
    [(fn []
       (let [counters
             (:report-counters (test/get-current-env))]

         (test/report (assoc counters :type :summary))
         (test/report (assoc counters :type :end-run-tests))
         (test/clear-env!)))])))

(defn -main
  [& _args]
  (run-tests!))

(set! *main-cli-fn* -main)
