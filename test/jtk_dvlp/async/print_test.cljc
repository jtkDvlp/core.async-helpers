(ns jtk-dvlp.async.print-test
  "Tests for `jtk-dvlp.async.print` — debug output of what a channel
   ends up with.

   All of the substance sits in `<debug`: take the value out, hand it to
   the print function, and in the failure case hand it the error instead
   of the value. `<println` and `<pprint` are only that with a fixed
   print function — for those the test checks that the output actually
   comes out."

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
   [jtk-dvlp.async.print :as print]
   [jtk-dvlp.async.test-support :refer [deftest-async]]))


;;; --- helpers ----------------------------------------------------------

(defn- <failing
  [message]
  (a/go
    (throw (ex-info message {:code :test-error}))))

(defn- <captured-output
  "Runs `<print-something`, waits for it, and yields a channel with
   everything that went to standard output along the way.

   NOTE: The output cannot be captured the same way on both platforms.
   On the JVM core.async carries the binding frame across the parking
   points of a go block, so `binding` still holds after a `<!`. In
   ClojureScript there is no such frame — a `binding` would long be
   restored by the time the callback runs. There `*print-fn*` is set and
   put back by hand instead."
  [<print-something]
  #?(:clj
     (a/go
       (let [writer (java.io.StringWriter.)]
         (binding [*out* writer]
           (core-async/<! (<print-something)))
         (str writer)))

     :cljs
     (a/go
       (let [collected
             (atom [])

             original-print-fn
             *print-fn*]

         (set! *print-fn* (fn [& args] (swap! collected conj (apply str args))))
         (core-async/<! (<print-something))
         (set! *print-fn* original-print-fn)
         (string/join @collected)))))


;;; --- <debug -----------------------------------------------------------

(deftest-async <debug-hands-the-value-to-the-print-fn
  (let [printed
        (atom [])

        c
        (print/<debug #(swap! printed conj %) (a/go :value))]

    (core-async/<! c)
    (is (= [:value] @printed))))

(deftest-async <debug-hands-the-exception-to-the-print-fn
  ;; NOTE: The actual point of it. A `go` channel carries the exception
  ;;       as a value; anyone just running `println` on that would never
  ;;       see the exception thrown. `<debug` takes it out with `<!` and
  ;;       catches.
  (let [printed
        (atom [])

        c
        (print/<debug #(swap! printed conj %) (<failing "broken"))]

    (core-async/<! c)
    (is (= 1 (count @printed)))
    (is (a/exception? (first @printed)))
    (is (= "broken" (ex-message (first @printed))))))

(deftest-async <debug-yields-a-channel
  (is (a/chan? (print/<debug (fn [_]) (a/go :value)))))


;;; --- <println / <pprint -----------------------------------------------

(deftest-async <println-writes-the-value
  (let [output (a/<! (<captured-output #(print/<println (a/go :value))))]
    (is (string/includes? output ":value"))))

(deftest-async <println-writes-the-exception
  (let [output
        (a/<! (<captured-output #(print/<println (<failing "broken"))))]

    (is (string/includes? output "broken"))))

(deftest-async <pprint-writes-the-value
  (let [output
        (a/<! (<captured-output
               #(print/<pprint (a/go {:a 1, :b [2 3]}))))]

    (is (string/includes? output ":a"))
    (is (string/includes? output ":b"))))
