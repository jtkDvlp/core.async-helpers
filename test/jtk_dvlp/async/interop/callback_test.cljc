(ns jtk-dvlp.async.interop.callback-test
  "Tests for `jtk-dvlp.async.interop.callback` — the bridge from
   callback-based APIs to channels.

   WATCHOUT: `cb->c` is a macro that *rewrites* its expression: it looks
   for the symbols `callback`, `resolve` and `reject` inside and puts
   its own functions in their place. That is why those marks stand bare
   in the code below without ever being defined — that is how they are
   meant to be used. Moving one into a helper function takes it out of
   the macro's sight; there is a test for that too."

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
   [jtk-dvlp.async.interop.callback :as callback]
   [jtk-dvlp.async.test-support :refer [deftest-async]])

  #?(:clj
     (:import
      [clojure.lang ExceptionInfo])))


;;; --- callback APIs, shaped the way one meets them in the wild ---------

(defn- call-with-result-last
  "Takes the callback as its last argument — the most common shape."
  [value on-result]
  (on-result value)
  nil)

(defn- call-with-success-and-failure
  "Takes two callbacks, one for success, one for failure."
  [value fail? on-success on-failure]
  (if fail?
    (on-failure value)
    (on-success value))
  nil)

(defn- call-repeatedly
  "Calls the callback once per value — an event source, not a single
   result."
  [values on-event]
  (doseq [value values]
    (on-event value))
  nil)

(defn- call-and-throw
  [exception _on-result]
  (throw exception))

(defn- foreign-exception
  [message]
  #?(:clj (RuntimeException. message)
     :cljs (js/Error. message)))


;;; --- cb->c ------------------------------------------------------------

(deftest-async cb->c-appends-resolve-when-no-mark-is-given
  ;; NOTE: Without a mark `cb->c` assumes the callback is the last
  ;;       argument and appends itself.
  (is (= :value (a/<! (callback/cb->c (call-with-result-last :value))))))

(deftest-async cb->c-finds-the-callback-mark
  (is (= :value (a/<! (callback/cb->c
                       (call-with-result-last :value callback))))))

(deftest-async cb->c-finds-the-resolve-mark
  (is (= :ok (a/<! (callback/cb->c
                    (call-with-success-and-failure
                     :ok false resolve reject))))))

(deftest-async cb->c-wraps-the-rejection-as-exception-info
  (let [result
        (core-async/<! (callback/cb->c
                        (call-with-success-and-failure
                         :bad true resolve reject)))]

    (is (a/exception? result))
    (is (= {:code :callback-error, :error :bad} (ex-data result))
        "the rejected plain value survives under `:error`")))

(deftest-async cb->c-finds-marks-inside-inline-functions
  ;; NOTE: The case from the docstring of `cb->c`: the error is to be
  ;;       reshaped before rejecting. That only works because the macro
  ;;       walks the expression recursively (`postwalk`) — but the
  ;;       function has to stand inline for it.
  (let [result
        (core-async/<!
         (callback/cb->c
          (call-with-success-and-failure
           :payload true
           resolve
           (fn [error]
             (->> {:code :mine, :payload error}
                  (ex-info "my own failure")
                  (reject))))))]

    (is (a/exception? result))
    (is (= "my own failure" (ex-message result)))
    (is (= {:code :mine, :payload :payload} (ex-data result)))))

(deftest-async cb->c-passes-exception-info-through
  ;; NOTE: Test for issue #4 ("Carries callback ex-info"). Whoever
  ;;       rejects with an `ExceptionInfo` keeps its data.
  (let [rejection
        (ex-info "my own failure" {:code :mine, :details 42})

        result
        (core-async/<!
         (callback/cb->c
          (call-with-success-and-failure
           :whatever true
           resolve
           (fn [_error] (reject rejection)))))]

    (is (identical? rejection result))
    (is (= {:code :mine, :details 42} (ex-data result)))))

(deftest-async cb->c-closes-the-channel-after-the-first-value
  (let [c (callback/cb->c (call-repeatedly [1 2 3] callback))]
    (is (= 1 (a/<! c)))
    (is (nil? (core-async/<! c))
        "auto-close? is the default: after the first value it is over")))

(deftest-async cb->c-keeps-the-channel-open-without-auto-close
  ;; WATCHOUT: With `auto-close?` false the caller owns closing the
  ;;           channel. Without that the channel stays open forever and
  ;;           every reader on it waits forever.
  (let [c (callback/cb->c (call-repeatedly [1 2 3] callback) false)]
    (is (= 1 (a/<! c)))
    (is (= 2 (a/<! c)))
    (is (= 3 (a/<! c)))
    (core-async/close! c)
    (is (nil? (core-async/<! c)))))

(deftest-async cb->c-carries-an-error-from-the-call-itself
  (let [result
        (core-async/<!
         (callback/cb->c
          (call-and-throw (ex-info "the call is broken" {:code :call})
                          callback)))]

    (is (a/exception? result))
    (is (= {:code :call} (ex-data result)))))

(deftest-async cb->c-wraps-foreign-exceptions-from-the-call
  (let [result
        (core-async/<!
         (callback/cb->c
          (call-and-throw (foreign-exception "raw") callback)))]

    (is (a/exception? result))
    (is (= {:code :callback-based-function-error} (ex-data result)))))


;;; --- <cb! -------------------------------------------------------------

(deftest-async <cb!-takes-the-callback-value
  (is (= :value (callback/<cb! (call-with-result-last :value)))))

(deftest-async <cb!-throws-the-rejection
  (let [result
        (core-async/<!
         (a/go
           (callback/<cb!
            (call-with-success-and-failure :bad true resolve reject))))]

    (is (a/exception? result))
    (is (= {:code :callback-error, :error :bad} (ex-data result)))))

(deftest-async <cb!-allows-try-catch
  (let [result
        (try
          (callback/<cb!
           (call-with-success-and-failure :bad true resolve reject))
          :never-reached
          (catch ExceptionInfo e
            [:caught (ex-data e)]))]

    (is (= [:caught {:code :callback-error, :error :bad}] result))))
