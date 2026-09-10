(ns jtk-dvlp.async-test
  "Tests for `jtk-dvlp.async` — the core of the error propagation.

   Every function is checked twice: once for the happy path, once for
   the failure. The failure is the actual subject matter here — an
   exception is supposed to travel a channel as a value and be thrown
   again when taken with `<!`."

  (:require
   [clojure.string :as string]

   #?(:clj
      [clojure.test :refer [deftest is testing]]

      :cljs
      [cljs.test :refer [deftest is testing]])

   #?(:clj
      [clojure.core.async :as core-async]

      :cljs
      [cljs.core.async :as core-async])

   [jtk-dvlp.async :as a]
   [jtk-dvlp.async.test-support :refer [deftest-async]])

  #?(:clj
     (:import
      [clojure.lang ExceptionInfo])))


;;; --- helpers ----------------------------------------------------------
;;
;; WATCHOUT: Where `core-async/<!` appears below instead of `a/<!`, that
;;           is deliberate: the test wants the channel's raw value.
;;           `a/<!` would throw the carried error and the test would
;;           never reach its assertion.

(def ^:private error-data
  {:code :test-error})

(defn- <value
  "Channel yielding `x` — optionally only after `delay-ms`."
  ([x]
   (<value x 0))

  ([x delay-ms]
   (a/go
     (a/<! (core-async/timeout delay-ms))
     x)))

(defn- <failing
  "Channel carrying an `ExceptionInfo`."
  ([]
   (<failing "test failure"))

  ([message]
   (a/go
     (throw (ex-info message error-data)))))

(defn- <increment
  [x]
  (a/go
    (inc x)))

(defn- foreign-exception
  "An exception that is *not* an `ExceptionInfo` — `go` is supposed to
   convert it into one."
  [message]
  #?(:clj (RuntimeException. message)
     :cljs (js/Error. message)))

(defn- cause-message
  [exception]
  (let [cause (ex-cause exception)]
    #?(:clj (.getMessage ^Throwable cause)
       :cljs (.-message cause))))

(defn- stacktrace-text
  "The stack trace as one piece of text, searchable on both platforms."
  [exception]
  #?(:clj
     (->> (.getStackTrace ^Throwable exception)
          (mapv str)
          (string/join "\n"))

     :cljs
     (.-stack exception)))


;;; --- chan? / exception? -----------------------------------------------

(deftest chan?-detects-channels
  (is (a/chan? (core-async/chan)))
  (is (a/chan? (core-async/promise-chan)))
  (is (a/chan? (a/go 1)))

  (testing "anything else is not a channel"
    (is (not (a/chan? nil)))
    (is (not (a/chan? 42)))
    (is (not (a/chan? [1 2 3])))))

(deftest exception?-only-accepts-exception-info
  (is (a/exception? (ex-info "x" {})))

  (testing "a foreign exception does not count"
    ;; NOTE: On purpose, not an oversight. `go` converts every foreign
    ;;       exception into an `ExceptionInfo` first; only then is it a
    ;;       value the propagation can carry.
    (is (not (a/exception? (foreign-exception "x")))))

  (testing "plain values are not exceptions"
    (is (not (a/exception? nil)))
    (is (not (a/exception? 42)))
    (is (not (a/exception? {:code :x})))))

(deftest throwable?-detects-throwables
  (is (a/throwable? (ex-info "x" {})))
  (is (a/throwable? (foreign-exception "x")))

  (testing "ordinary values cannot be thrown"
    (is (not (a/throwable? nil)))
    (is (not (a/throwable? 42)))
    (is (not (a/throwable? :bad)))
    (is (not (a/throwable? {:code :x})))))

(deftest ->exception-passes-exception-info-through
  ;; NOTE: Whoever already holds an `ExceptionInfo` meant its message
  ;;       and data — those stay untouched.
  (let [original (ex-info "mine" {:code :mine, :details 42})]
    (is (identical? original
                    (a/->exception "shell" :shell original)))))

(deftest ->exception-makes-foreign-error-the-cause
  (let [result
        (a/->exception "shell" :shell (foreign-exception "raw"))]

    (is (a/exception? result))
    (is (= "shell" (ex-message result)))
    (is (= {:code :shell} (ex-data result)))
    (is (= "raw" (cause-message result)))))

(deftest ->exception-lifts-a-plain-value-into-the-data
  ;; NOTE: The case this exists for. `clojure.core/ex-info` demands a
  ;;       `Throwable` in the cause position and throws a
  ;;       ClassCastException on anything else, while ClojureScript
  ;;       takes whatever it is given. A plain value therefore belongs
  ;;       in the data, not in the cause — and the same way on both
  ;;       platforms.
  (let [result
        (a/->exception "shell" :shell :just-a-value)]

    (is (a/exception? result))
    (is (= {:code :shell, :error :just-a-value} (ex-data result)))
    (is (nil? (ex-cause result))))

  (testing "nil and collections as well"
    (is (= {:code :shell, :error nil}
           (ex-data (a/->exception "shell" :shell nil))))
    (is (= {:code :shell, :error {:a 1}}
           (ex-data (a/->exception "shell" :shell {:a 1}))))))


;;; --- go / go-loop -----------------------------------------------------

(deftest-async go-yields-value
  (is (= 42 (a/<! (a/go 42)))))

(deftest-async go-carries-exception-info-as-value
  (let [result
        (core-async/<! (a/go (throw (ex-info "broken" {:code :broken}))))]

    (is (a/exception? result))
    (is (= "broken" (ex-message result)))
    (is (= {:code :broken} (ex-data result)))))

(deftest-async go-converts-foreign-exception
  (let [result
        (core-async/<! (a/go (throw (foreign-exception "raw"))))]

    (is (a/exception? result))
    (is (= {:code :unknown} (ex-data result))
        "the conversion marks itself as one")
    (is (= "raw" (cause-message result))
        "the original exception survives as the cause")))

(deftest-async go-loop-runs-to-completion
  (is (= 10 (a/<! (a/go-loop [sum 0, [x & more] [1 2 3 4]]
                    (if x
                      (recur (+ sum x) more)
                      sum))))))

(deftest-async go-loop-carries-error
  (let [result
        (core-async/<! (a/go-loop [n 0]
                         (if (< n 3)
                           (recur (inc n))
                           (throw (ex-info "in the loop" {:code :loop})))))]

    (is (a/exception? result))
    (is (= {:code :loop} (ex-data result)))))


;;; --- <! ---------------------------------------------------------------

(deftest-async <!-yields-value
  (is (= :value (a/<! (<value :value)))))

(deftest-async <!-allows-try-catch-in-go-block
  (let [result
        (try
          (a/<! (<failing "burst"))
          :never-reached
          (catch ExceptionInfo e
            [:caught (ex-message e)]))]

    (is (= [:caught "burst"] result))))

(deftest-async <!-propagates-error-up-the-go-stack
  ;; NOTE: This is the heart of the library — the error from the
  ;;       innermost channel arrives at the top with nothing in between
  ;;       having to pass it on.
  (let [<inner  (fn [] (<failing "all the way down"))
        <middle (fn [] (a/go (inc (a/<! (<inner)))))
        <outer  (fn [] (a/go (inc (a/<! (<middle)))))

        result
        (core-async/<! (<outer))]

    (is (a/exception? result))
    (is (= "all the way down" (ex-message result)))))


;;; --- athrow / stack trace ---------------------------------------------

(deftest-async athrow-throws-the-given-exception
  (let [original
        (ex-info "original" {:code :original})

        caught
        (try
          (a/athrow original)
          nil
          (catch ExceptionInfo e
            e))]

    (is (identical? original caught))))

(deftest-async stacktrace-carries-the-async-boundary
  ;; NOTE: Test for issue #5 ("Dealing with context loss from
  ;;       compaction"). Without the extension the stack trace ends at
  ;;       the go block's thread boundary, and one can no longer see who
  ;;       asked for the failing channel in the first place. The
  ;;       `ASYNC_BOUNDARY` marker separates the two halves.
  (let [result
        (core-async/<! (a/go (a/<! (<failing))))]

    (is (a/exception? result))
    (is (string/includes? (stacktrace-text result) "ASYNC_BOUNDARY")
        "the stack trace has to reach past the go block boundary")))


;;; --- <?! / <? ---------------------------------------------------------

(deftest-async <?!-takes-from-channel
  (is (= :from-channel (a/<?! (<value :from-channel)))))

(deftest-async <?!-passes-non-channel-through
  (is (= 42 (a/<?! 42)))
  (is (= [1 2] (a/<?! [1 2])))
  (is (nil? (a/<?! nil))))

(deftest-async <?!-throws-carried-error
  (let [result
        (core-async/<! (a/go (a/<?! (<failing))))]

    (is (a/exception? result))))

(deftest-async <?-behaves-like-<?!
  ;; NOTE: `<?` is deprecated but still public API. This pins down that
  ;;       the old form does not change behaviour.
  (is (= :value (a/<? (<value :value))))
  (is (= 42 (a/<? 42))))


;;; --- blocking variants (Clojure only) ---------------------------------

#?(:clj
   (deftest <!!-takes-blocking
     (is (= :value (a/<!! (<value :value 10))))))

#?(:clj
   (deftest <!!-throws-carried-error
     (is (thrown-with-msg? ExceptionInfo #"test failure"
                           (a/<!! (<failing))))))

#?(:clj
   (deftest <?!!-handles-channel-and-value
     (is (= :value (a/<?!! (<value :value))))
     (is (= 42 (a/<?!! 42)))
     (is (thrown-with-msg? ExceptionInfo #"test failure"
                           (a/<?!! (<failing))))))

#?(:clj
   (deftest thread-call-yields-result
     (is (= 42 (a/<!! (a/thread-call (fn [] 42)))))))

#?(:clj
   (deftest thread-call-accepts-a-workload
     ;; NOTE: The argument routes work to a pool per kind. Older
     ;;       core.async versions do not know it and have one pool for
     ;;       everything — the call still has to go through, see
     ;;       `thread-call-takes-workload?`.
     (doseq [workload [:io :compute :mixed]]
       (is (= workload (a/<!! (a/thread-call (fn [] workload) workload)))
           (str "workload " workload)))))

#?(:clj
   (deftest thread-call-carries-exception-info
     (let [result
           (core-async/<!!
            (a/thread-call
             (fn [] (throw (ex-info "in the thread" {:code :thread})))))]

       (is (a/exception? result))
       (is (= {:code :thread} (ex-data result))))))

#?(:clj
   (deftest thread-call-converts-foreign-exception
     (let [result
           (core-async/<!!
            (a/thread-call
             (fn [] (throw (foreign-exception "raw")))))]

       (is (a/exception? result))
       (is (= {:code :unknown} (ex-data result)))
       (is (= "raw" (cause-message result))))))

#?(:clj
   (deftest thread-yields-result
     (is (= 42 (a/<!! (a/thread 42))))))

#?(:clj
   (deftest thread-carries-error
     (let [result
           (core-async/<!!
            (a/thread (throw (ex-info "in the thread" {:code :thread}))))]

       (is (a/exception? result))
       (is (= {:code :thread} (ex-data result))))))


;;; --- map / all --------------------------------------------------------

(deftest-async map-combines-channel-values
  (is (= 6 (a/<! (a/map + [(<value 1) (<value 2) (<value 3)])))))

(deftest-async map-carries-error-from-a-channel
  (let [result
        (core-async/<! (a/map + [(<value 1) (<failing) (<value 3)]))]

    (is (a/exception? result))
    (is (= "test failure" (ex-message result)))))

(deftest-async map-carries-error-from-the-function
  (let [<broken
        (fn [& _] (throw (ex-info "f is broken" {:code :f})))

        result
        (core-async/<! (a/map <broken [(<value 1)]))]

    (is (a/exception? result))
    (is (= {:code :f} (ex-data result)))))

(deftest-async all-collects-every-value
  (is (= [1 2 3] (a/<! (a/all [(<value 1) (<value 2) (<value 3)])))))

(deftest-async map-and-all-handle-no-channels-at-all
  ;; NOTE: `core.async/map` waits forever here — it counts down the
  ;;       channels it has and with none it never reaches zero. That
  ;;       propagated into `all` and `amap`, so an empty collection took
  ;;       the caller down with it. The answer is now `(f)`, the same
  ;;       one `clojure.core` gives for folding over nothing.
  (is (= [] (a/<! (a/all []))))
  (is (= 0 (a/<! (a/map + []))))
  (is (= [] (a/<! (a/map vector [])))))

(deftest-async all-carries-error
  (let [result
        (core-async/<! (a/all [(<value 1) (<failing)]))]

    (is (a/exception? result))))


;;; --- consume! ---------------------------------------------------------

(deftest-async consume!-calls-f-for-every-value
  (let [seen
        (atom [])

        done
        (core-async/chan)

        collect!
        (fn [v]
          (swap! seen conj v)
          (when (= v 3)
            (core-async/close! done)))]

    (is (nil? (a/consume! (core-async/to-chan! [1 2 3]) collect!))
        "`consume!` returns right away and yields nothing")

    (a/<! done)
    (is (= [1 2 3] @seen))))

(deftest-async consume!-passes-a-false-through
  ;; NOTE: A closed channel yields `nil`, and that is the only thing
  ;;       meant to end the loop. Testing the value for truthiness
  ;;       instead would make a `false` in the stream look like the end
  ;;       of it and silently drop everything after it.
  (let [seen
        (atom [])

        done
        (core-async/chan)

        collect!
        (fn [v]
          (swap! seen conj v)
          (when (= v 3)
            (core-async/close! done)))]

    (a/consume! (core-async/to-chan! [1 false 3]) collect!)
    (a/<! done)
    (is (= [1 false 3] @seen))))


;;; --- smap / chain -----------------------------------------------------

(deftest-async smap-maps
  (is (= [2 3 4] (a/<! (a/smap <increment [1 2 3])))))

(deftest-async smap-handles-several-collections
  (let [<sum (fn [a b] (a/go (+ a b)))]
    (is (= [11 22 33] (a/<! (a/smap <sum [1 2 3] [10 20 30]))))))

(deftest-async smap-runs-sequentially
  ;; NOTE: What sets this apart from `amap` is the order of *execution*,
  ;;       not the order of the results. So the first element gets the
  ;;       longest wait: were the calls concurrent, it would finish last.
  (let [finished
        (atom [])

        <slowest-first
        (fn [x]
          (a/go
            (a/<! (core-async/timeout (- 30 (* 10 x))))
            (swap! finished conj x)
            x))]

    (is (= [1 2 3] (a/<! (a/smap <slowest-first [1 2 3]))))
    (is (= [1 2 3] @finished)
        "sequential: the slowest x=1 still finishes first")))

(deftest-async smap-keeps-falsy-elements
  ;; NOTE: `nil` and `false` are values like any other. Ending the
  ;;       mapping on them would drop the rest of the collection without
  ;;       a word.
  (is (= [1 nil 3] (a/<! (a/smap (fn [x] (a/go x)) [1 nil 3]))))
  (is (= [1 false 3] (a/<! (a/smap (fn [x] (a/go x)) [1 false 3])))))

(deftest-async smap-stops-at-the-shortest-collection
  ;; NOTE: Same rule as `clojure.core/map`.
  (let [<pair (fn [a b] (a/go [a b]))]
    (is (= [[1 :a] [2 :b]]
           (a/<! (a/smap <pair [1 2 3] [:a :b]))))))

(deftest-async smap-handles-an-empty-collection
  (is (= [] (a/<! (a/smap (fn [x] (a/go x)) [])))))

(deftest-async smap-carries-error
  (let [result
        (core-async/<! (a/smap (fn [_] (<failing)) [1 2]))]

    (is (a/exception? result))))

(deftest-async chain-is-an-alias-for-smap
  (is (identical? a/smap a/chain))
  (is (= [2 3] (a/<! (a/chain <increment [1 2])))))


;;; --- amap -------------------------------------------------------------

(deftest-async amap-maps
  (is (= [2 3 4] (a/<! (a/amap <increment [1 2 3])))))

(deftest-async amap-handles-several-collections
  (let [<sum (fn [a b] (a/go (+ a b)))]
    (is (= [11 22 33] (a/<! (a/amap <sum [1 2 3] [10 20 30]))))))

(deftest-async amap-keeps-the-result-order
  ;; NOTE: The counterpart to `smap-runs-sequentially`: execution may
  ;;       overtake itself, the result may not.
  (let [<slowest-first
        (fn [x]
          (a/go
            (a/<! (core-async/timeout (- 30 (* 10 x))))
            x))]

    (is (= [1 2 3] (a/<! (a/amap <slowest-first [1 2 3]))))))

(deftest-async amap-keeps-falsy-results
  ;; NOTE: A go block whose body yields `nil` closes its channel without
  ;;       putting anything on it, and `core.async/map` cannot tell that
  ;;       apart from a channel that is done — so a single `nil` used to
  ;;       take the whole call down and `amap` yielded `nil` instead of
  ;;       a vector. `amap` boxes each result to keep them apart.
  (is (= [1 nil 3] (a/<! (a/amap (fn [x] (a/go x)) [1 nil 3]))))
  (is (= [1 false 3] (a/<! (a/amap (fn [x] (a/go x)) [1 false 3])))))

(deftest-async amap-and-smap-agree-on-falsy-values
  ;; NOTE: The two differ in how they execute, never in what they
  ;;       return. A regression in either one shows up here.
  (let [<identity (fn [x] (a/go x))
        input     [nil 1 false 2 nil]]

    (is (= (a/<! (a/smap <identity input))
           (a/<! (a/amap <identity input))))))

(deftest-async amap-handles-an-empty-collection
  (is (= [] (a/<! (a/amap (fn [x] (a/go x)) [])))))

(deftest-async amap-carries-error
  (let [result
        (core-async/<! (a/amap (fn [_] (<failing)) [1 2]))]

    (is (a/exception? result))))


;;; --- reduce / areduce / into ------------------------------------------

(deftest-async reduce-folds-channel-values
  (is (= 6 (a/<! (a/reduce + 0 (core-async/to-chan! [1 2 3]))))))

(deftest-async reduce-carries-error-from-the-stream
  (let [source
        (core-async/to-chan! [1 (ex-info "in the stream" {:code :stream}) 3])

        result
        (core-async/<! (a/reduce + 0 source))]

    (is (a/exception? result))
    (is (= {:code :stream} (ex-data result)))))

(deftest-async reduce-carries-error-from-the-function
  (let [broken
        (fn [_ _] (throw (ex-info "f is broken" {:code :f})))

        result
        (core-async/<! (a/reduce broken 0 (core-async/to-chan! [1 2])))]

    (is (a/exception? result))
    (is (= {:code :f} (ex-data result)))))

(deftest-async areduce-folds-asynchronously
  (let [<sum (fn [accu x] (a/go (+ accu x)))]
    (is (= 6 (a/<! (a/areduce <sum 0 [1 2 3]))))))

(deftest-async areduce-keeps-falsy-items
  ;; NOTE: Walking the seq rather than testing the item — otherwise the
  ;;       reduction ends at the first `nil` or `false` in `coll`.
  (let [<conj (fn [result x] (a/go (conj result x)))]
    (is (= [1 nil 3] (a/<! (a/areduce <conj [] [1 nil 3]))))
    (is (= [1 false 3] (a/<! (a/areduce <conj [] [1 false 3]))))))

(deftest-async areduce-handles-an-empty-collection
  (is (= :init (a/<! (a/areduce (fn [_ _] (a/go :never)) :init [])))))

(deftest-async areduce-carries-error
  (let [result
        (core-async/<! (a/areduce (fn [_ _] (<failing)) 0 [1 2]))]

    (is (a/exception? result))))

(deftest-async into-collects-into-the-collection
  (is (= [1 2 3] (a/<! (a/into [] (core-async/to-chan! [1 2 3])))))
  (is (= #{1 2 3} (a/<! (a/into #{} (core-async/to-chan! [1 2 3]))))))

(deftest-async into-carries-error
  (let [source
        (core-async/to-chan! [1 (ex-info "in the stream" {:code :stream}) 3])

        result
        (core-async/<! (a/into [] source))]

    (is (a/exception? result))))


;;; --- awalk / apostwalk / aprewalk -------------------------------------

(defn- <double-numbers
  [x]
  (a/go
    (if (number? x)
      (* 2 x)
      x)))

(deftest-async apostwalk-walks-nested-maps
  (is (= {:a 2, :b {:c 4}}
         (a/<! (a/apostwalk <double-numbers {:a 1, :b {:c 2}})))))

(deftest-async apostwalk-walks-vectors
  (is (= [2 4 6] (a/<! (a/apostwalk <double-numbers [1 2 3])))))

(deftest-async apostwalk-walks-lists
  (is (= '(2 4 6) (a/<! (a/apostwalk <double-numbers '(1 2 3))))))

(deftest-async apostwalk-walks-sets
  (is (= #{2 4} (a/<! (a/apostwalk <double-numbers #{1 2})))))

(deftest-async apostwalk-walks-scalars
  (is (= 4 (a/<! (a/apostwalk <double-numbers 2)))))

(deftest-async apostwalk-keeps-nil-inside-collections
  ;; NOTE: `apostwalk` walks through `amap`, so it used to lose a whole
  ;;       collection over a single `nil` in it.
  (is (= [2 nil 6] (a/<! (a/apostwalk <double-numbers [1 nil 3]))))
  (is (= {:a nil, :b 4} (a/<! (a/apostwalk <double-numbers {:a nil, :b 2})))))

(deftest-async apostwalk-carries-error
  (let [result
        (core-async/<! (a/apostwalk (fn [_] (<failing)) {:a 1}))]

    (is (a/exception? result))))

(deftest-async aprewalk-walks-nested-maps
  (is (= {:a 2, :b {:c 4}}
         (a/<! (a/aprewalk <double-numbers {:a 1, :b {:c 2}})))))

(deftest-async aprewalk-walks-outermost-first
  ;; NOTE: What sets this apart from `apostwalk`. `<replace-marker`
  ;;       swaps a marker for a structure; only because `aprewalk`
  ;;       starts on the outside does that *inserted* structure get
  ;;       visited as well.
  (let [<replace-marker
        (fn [x]
          (a/go
            (if (= :marker x)
              [:replaced 1]
              x)))]

    (is (= [:replaced 1] (a/<! (a/aprewalk <replace-marker :marker))))))

(deftest-async awalk-calls-inner-and-outer
  (let [<inner (fn [x] (a/go (if (number? x) (inc x) x)))
        <outer (fn [form] (a/go [:outer form]))]

    (is (= [:outer [2 3]] (a/<! (a/awalk <inner <outer [1 2]))))))

(deftest-async awalk-walks-map-entries
  (let [<inner (fn [x] (a/go (if (number? x) (* 10 x) x)))
        <outer (fn [form] (a/go form))
        entry  (first {:a 1})

        result
        (a/<! (a/awalk <inner <outer entry))]

    (is (= [:a 10] (vec result)))))

(defrecord Point [x y])

(deftest-async awalk-walks-records
  (is (= (->Point 2 4)
         (a/<! (a/apostwalk <double-numbers (->Point 1 2))))))
