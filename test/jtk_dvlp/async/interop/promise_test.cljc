(ns jtk-dvlp.async.interop.promise-test
  "Tests for `jtk-dvlp.async.interop.promise` — the bridge between
   channels and promises.

   WATCHOUT: \"Promise\" means different things on the two platforms. In
   ClojureScript it is a `js/Promise` with `then`/`catch`; on the JVM it
   is a `clojure.core/promise`, which has neither and only takes a
   single `deliver`. The library evens that out; where it still shows
   through, the test says which platform it means."

  (:require
   #?(:clj
      [clojure.test :refer [deftest is testing]]

      :cljs
      [cljs.test :refer [deftest is testing]])

   #?(:clj
      [clojure.core.async :as core-async]

      :cljs
      [cljs.core.async :as core-async])

   [jtk-dvlp.async :as a]
   [jtk-dvlp.async.interop.promise :as promise]
   [jtk-dvlp.async.test-support :refer [deftest-async]])

  #?(:clj
     (:import
      [clojure.lang ExceptionInfo])))


;;; --- helpers ----------------------------------------------------------

(def ^:private error-data
  {:code :test-error})

(defn- <value
  ([x]
   (<value x 0))

  ([x delay-ms]
   (a/go
     (a/<! (core-async/timeout delay-ms))
     x)))

(defn- <failing
  ([]
   (<failing "test failure"))

  ([message]
   (a/go
     (throw (ex-info message error-data)))))

(defn- resolved-promise
  [value]
  #?(:clj  (doto (promise) (deliver value))
     :cljs (js/Promise.resolve value)))

(defn- rejected-promise
  "A promise that fails with `error`.

   NOTE: On the JVM there is no rejection — a `promise` only knows
   `deliver`. A rejection there is simply a delivered exception value,
   which `p->c` puts onto the channel just the same. The outcome is
   identical on both platforms: an `ExceptionInfo` on the channel."
  [error]
  #?(:clj  (doto (promise) (deliver error))
     :cljs (js/Promise.reject error)))


;;; --- p->c -------------------------------------------------------------

(deftest-async p->c-yields-the-resolved-value
  (is (= :value (a/<! (promise/p->c (resolved-promise :value))))))

(deftest-async p->c-carries-the-rejection-as-exception-info
  (let [rejection
        (ex-info "rejected" error-data)

        result
        (core-async/<! (promise/p->c (rejected-promise rejection)))]

    (is (a/exception? result))
    (is (identical? rejection result)
        "an ExceptionInfo that already fits is not wrapped again")))

(deftest-async p->c-yields-the-value-repeatedly
  ;; WATCHOUT: `p->c` closes the channel once the value is on it — but
  ;;           it is a `promise-chan`, and that hands out its buffered
  ;;           value even when closed. "Closed" here means "takes
  ;;           nothing new", not "yields nothing more".
  (let [c (promise/p->c (resolved-promise :once))]
    (is (= :once (a/<! c)))
    (is (= :once (a/<! c)))
    (is (= :once (a/<! c)))))

#?(:cljs
   (deftest-async p->c-wraps-foreign-rejections
     ;; NOTE: ClojureScript only. On the JVM a rejection cannot be
     ;;       "foreign" — there is no rejection path there, see
     ;;       `rejected-promise`.
     (let [result
           (core-async/<!
            (promise/p->c (rejected-promise (js/Error. "raw"))))]

       (is (a/exception? result))
       (is (= {:code :promise-error} (ex-data result)))
       (is (= "raw" (.-message (ex-cause result)))))))

#?(:clj
   (def ^:private p->c-max-blocking-ms
     "Upper bound for how long the `p->c` call itself may take.

      NOTE: Test for issue #3 (\"Fixes `p->c` blocks calling thread\").
      On the JVM there is no `then` for a `promise`; without the detour
      through a `future`, `p->c` would sit there until delivery. Measured
      against a generous bound — this is about the order of magnitude
      (immediate rather than delivery time), not about milliseconds. The
      call itself measures under 1 ms; 500 ms leaves room for any load
      spike in CI and still catches the regression, because the test
      below only delivers after `deliver-delay-ms`."
     500))

#?(:clj
   (def ^:private deliver-delay-ms
     "How long the blocking test waits before delivering the promise.
      Has to be well above `p->c-max-blocking-ms` — otherwise the test
      would pass even with a blocking `p->c`."
     2000))

#?(:clj
   (deftest p->c-does-not-block-the-calling-thread
     (let [p
           (promise)

           _
           (future
             (Thread/sleep deliver-delay-ms)
             (deliver p :late))

           start
           (System/nanoTime)

           c
           (promise/p->c p)

           elapsed-ms
           (/ (- (System/nanoTime) start) 1e6)]

       (is (< elapsed-ms p->c-max-blocking-ms)
           (str "`p->c` blocked for " elapsed-ms " ms, allowed are "
                p->c-max-blocking-ms " ms"))

       (is (= :late (a/<!! c))
           "the value still arrives, just later"))))


;;; --- c->p -------------------------------------------------------------

(deftest-async c->p-yields-the-channel-value
  ;; NOTE: Going back through `p->c` keeps this platform-neutral —
  ;;       otherwise it would have to tell `then` and `deref` apart.
  (is (= :value (a/<! (promise/p->c (promise/c->p (<value :value)))))))

(deftest-async c->p-passes-the-error-on
  (let [result
        (core-async/<! (promise/p->c (promise/c->p (<failing))))]

    (is (a/exception? result))
    (is (= "test failure" (ex-message result)))))

#?(:clj
   (deftest c->p-yields-a-promise-on-the-jvm
     (is (= :value (deref (promise/c->p (<value :value)))))))

#?(:cljs
   (deftest c->p-yields-a-js-promise-in-clojurescript
     (is (instance? js/Promise (promise/c->p (<value :value))))))


;;; --- promise-chan -----------------------------------------------------

(deftest-async promise-chan-without-arguments-is-empty
  (let [p (promise/promise-chan)]
    (is (a/chan? p))
    (core-async/put! p :value)
    (is (= :value (a/<! p)))
    (is (= :value (a/<! p))
        "a promise-chan yields the same value any number of times")))

(deftest-async promise-chan-fills-through-resolve
  (let [p (promise/promise-chan
           (fn [resolve _reject]
             (resolve :resolved)))]

    (is (= :resolved (a/<! p)))))

(deftest-async promise-chan-wraps-the-rejection
  (let [p (promise/promise-chan
           (fn [_resolve reject]
             (reject :just-a-value)))

        result
        (core-async/<! p)]

    (is (a/exception? result))
    (is (= {:code :promise-error, :error :just-a-value} (ex-data result))
        "the rejected plain value survives under `:error`")))

(deftest-async promise-chan-passes-exception-info-through
  ;; NOTE: Test for issue #4 ("Carries promise ex-info"). Whoever
  ;;       rejects with an `ExceptionInfo` wants to keep its data, not
  ;;       receive a shell carrying `{:code :promise-error}`.
  (let [rejection
        (ex-info "my own failure" {:code :mine, :details 42})

        result
        (core-async/<! (promise/promise-chan
                        (fn [_resolve reject]
                          (reject rejection))))]

    (is (identical? rejection result))
    (is (= {:code :mine, :details 42} (ex-data result)))))


;;; --- ->promise-chan / promise-go --------------------------------------

(deftest-async ->promise-chan-makes-the-value-readable-again
  (let [p (promise/->promise-chan (<value :value))]
    (is (= :value (a/<! p)))
    (is (= :value (a/<! p)))
    (is (= :value (a/<! p)))))

(deftest-async ->promise-chan-carries-the-error
  (let [p (promise/->promise-chan (<failing))]
    (is (a/exception? (core-async/<! p)))
    (is (a/exception? (core-async/<! p))
        "on the second read as well")))

(deftest-async promise-go-yields-a-promise-chan
  (let [p (promise/promise-go 42)]
    (is (= 42 (a/<! p)))
    (is (= 42 (a/<! p))
        "unlike `go`, the result can be read more than once")))

(deftest-async promise-go-carries-error
  (let [p (promise/promise-go
           (throw (ex-info "in the promise-go" {:code :pgo})))

        result
        (core-async/<! p)]

    (is (a/exception? result))
    (is (= {:code :pgo} (ex-data result)))))

(deftest-async promise-go-allows-parking-in-its-body
  (is (= 3 (a/<! (promise/promise-go
                   (+ (a/<! (<value 1))
                      (a/<! (<value 2))))))))


;;; --- <p! / <p!! -------------------------------------------------------

(deftest-async <p!-takes-the-promise-value
  (is (= :value (promise/<p! (resolved-promise :value)))))

(deftest-async <p!-throws-the-rejection
  (let [result
        (core-async/<!
         (a/go (promise/<p! (rejected-promise
                             (ex-info "rejected" error-data)))))]

    (is (a/exception? result))
    (is (= "rejected" (ex-message result)))))

(deftest-async <p!-allows-try-catch
  (let [result
        (try
          (promise/<p! (rejected-promise (ex-info "rejected" error-data)))
          :never-reached
          (catch ExceptionInfo e
            [:caught (ex-message e)]))]

    (is (= [:caught "rejected"] result))))

#?(:clj
   (deftest <p!!-takes-blocking
     (is (= :value (promise/<p!! (resolved-promise :value))))))

#?(:clj
   (deftest <p!!-throws-the-rejection
     (is (thrown-with-msg?
          ExceptionInfo #"rejected"
          (promise/<p!! (rejected-promise
                         (ex-info "rejected" error-data)))))))
