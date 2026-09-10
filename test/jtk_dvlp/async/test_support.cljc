(ns jtk-dvlp.async.test-support
  "Scaffolding for this library's tests.

   The library is `.cljc` and has to behave the same on both platforms,
   so the tests should exist only once. The one thing standing in the
   way is how to wait: Clojure blocks (`<!!`), ClojureScript cannot and
   needs `cljs.test/async`.

   `deftest-async` wraps exactly that difference. Everything else in a
   test body is the same code on both platforms."

  #?(:cljs
     (:require-macros
      [jtk-dvlp.async.test-support :refer [deftest-async]]))

  #?(:clj
     (:require
      [clojure.test]
      [clojure.core.async]
      [jtk-dvlp.async])

     :cljs
     (:require
      [cljs.test]
      [cljs.core.async]
      [jtk-dvlp.async])))


(def timeout-ms
  "Upper bound for a single asynchronous test.

   NOTE: Deliberately generous. The slowest test in this suite waits on
   a 50 ms `timeout`; 5000 ms is two orders of magnitude above that. The
   bound therefore catches a real standstill — a channel nobody ever
   writes to — without going off on load spikes in CI. Without it such
   a test would hang until the job timeout instead of saying what is
   wrong."
  5000)

(defn timeout-message
  [test-name]
  (str "`" test-name "` hit the " timeout-ms
       " ms bound — the channel never delivered."))

(defn unexpected-exception-message
  [test-name exception]
  (str "`" test-name "` threw unexpectedly: " (pr-str exception)))

#?(:clj
   (defmacro deftest-async
     "Like `clojure.test/deftest` and `cljs.test/deftest`, but `body`
      runs inside a `go` block and may use `<!`.

      The test fails if `body` throws, or if the channel does not
      deliver within `timeout-ms`.

      WATCHOUT: A test body without an `is` counts as passing. Anyone
      adding a test here asserts at least once — otherwise the test
      tests nothing and does not say so."
     [test-name & body]
     (let [test-label
           (str test-name)]

       (if (:ns &env)
         `(cljs.test/deftest ~test-name
            (cljs.test/async done#
              ;; NOTE: `cljs.core.async/go` and `alts!` on purpose,
              ;;       rather than our own: this wants the channel's
              ;;       raw value instead of having it thrown. Otherwise
              ;;       the test could not report the exception as a
              ;;       failure.
              (cljs.core.async/go
                (let [test-channel#
                      (jtk-dvlp.async/go ~@body)

                      [result# port#]
                      (cljs.core.async/alts!
                       [test-channel#
                        (cljs.core.async/timeout
                         jtk-dvlp.async.test-support/timeout-ms)])]

                  (cond
                    (not= port# test-channel#)
                    (cljs.test/is
                     false
                     (jtk-dvlp.async.test-support/timeout-message
                      ~test-label))

                    (jtk-dvlp.async/exception? result#)
                    (cljs.test/is
                     false
                     (jtk-dvlp.async.test-support/unexpected-exception-message
                      ~test-label result#)))

                  (done#)))))

         `(clojure.test/deftest ~test-name
            ;; NOTE: See the ClojureScript branch — `core.async/alts!!`
            ;;       is deliberate, to see the raw value.
            (let [test-channel#
                  (jtk-dvlp.async/go ~@body)

                  [result# port#]
                  (clojure.core.async/alts!!
                   [test-channel#
                    (clojure.core.async/timeout
                     jtk-dvlp.async.test-support/timeout-ms)])]

              (cond
                (not= port# test-channel#)
                (clojure.test/is
                 false
                 (jtk-dvlp.async.test-support/timeout-message
                  ~test-label))

                (jtk-dvlp.async/exception? result#)
                (clojure.test/is
                 false
                 (jtk-dvlp.async.test-support/unexpected-exception-message
                  ~test-label result#)))))))))
