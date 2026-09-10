(ns jtk-dvlp.async
  "Drop-in replacements for `core.async` that propagate errors.

   In plain `core.async` an exception thrown inside a `go` block is
   swallowed: the block's channel just closes and the caller sees
   `nil`. Here the exception travels as a *value* on the channel and is
   thrown again by `<!` in whichever go block takes it. Inside a `go`
   that throw is caught once more and becomes that block's result — so
   an error keeps climbing the go block stack until someone catches it,
   the way it would in synchronous code.

   Stack traces are stitched across the boundary, so a trace shows both
   the block that failed and the one that asked for the value. See
   `athrow`.

   Everything in this namespace propagates errors that way. Anything
   thrown that is not an `ExceptionInfo` is converted into one carrying
   `{:code :unknown}`, with the original as its cause.

   WATCHOUT: Do not mix these with `clojure.core.async`. Propagation
   only works because the error is an ordinary value on the channel — a
   plain `core.async/<!` in between takes that value silently, and the
   error is gone with nothing left to notice it by."

  (:refer-clojure
   :exclude [map pmap amap areduce reduce into])

  #?(:cljs
     (:require-macros
      [jtk-dvlp.async :refer [athrow go go-loop <! <?! <?]]))

  #?(:clj
     (:require
      [clojure.core.async :as async])

     :cljs
     (:require
      [cljs.core.async :as async]
      [cljs.core.async.impl.channels :refer [ManyToManyChannel]]))

  #?(:clj
     (:import
      [java.lang Thread StackTraceElement]
      [clojure.lang ExceptionInfo MapEntry]
      [clojure.core.async.impl.channels ManyToManyChannel]))

  ,,,)


(defn chan?
  "Is `x` a `core.async` channel?"
  [x]
  (instance? ManyToManyChannel x))

(defn exception?
  "Is `x` a carried error, i.e. an `ExceptionInfo`?

   Only `ExceptionInfo` counts. That is not an oversight: `go` converts
   everything else into one first, and only then is it a value the
   propagation can carry."
  [x]
  (instance? ExceptionInfo x))

#?(:clj
   (defmacro athrow
     "Throws `e`, after extending its stack trace with the current one.

      Without this the trace would end where the go block's thread
      began, and the caller that asked for the value would be invisible
      — the very context one needs to make sense of the error. The two
      halves are separated by an `ASYNC_BOUNDARY` marker: above it the
      frames of the block that failed, below it the frames of the block
      that took the value.

      Used by `<!`; rarely needed directly."
     [e]
     (if (:ns &env)
       `(let [exception#
              ~e

              exception-stacktrace#
              (aget exception# "stack")

              _#
              (cljs.core/js-invoke js/Error "captureStackTrace" exception#)

              current-stacktrace#
              (aget exception# "stack")

              boundary-trace-element#
              "--- ASYNC_BOUNDARY jtk-dvlp.async -1"

              async-stacktrace#
              (str
               exception-stacktrace#
               "\n"
               boundary-trace-element#
               "\n"
               current-stacktrace#)]

          (aset exception# "stack" async-stacktrace#)
          (throw exception#))
       `(let [exception#
              ~e

              exception-stacktrace#
              (-> exception#
                  (.getStackTrace))

              current-stacktrace#
              (-> (Thread/currentThread)
                  (.getStackTrace))

              boundary-trace-element#
              (StackTraceElement. "---" "ASYNC_BOUNDARY" "jtk-dvlp.async" -1)

              async-stacktrace#
              (concat
               exception-stacktrace#
               [boundary-trace-element#]
               current-stacktrace#)]

          (->> async-stacktrace#
               (into-array)
               (.setStackTrace exception#))

          (throw exception#)))))

#?(:clj
   (defmacro go
     "Like `core.async/go`, but an exception thrown in `body` becomes
      the block's result instead of being swallowed.

      An `ExceptionInfo` is carried as is; anything else is converted
      into one with `{:code :unknown}` and the original as its cause.
      Take the result with `<!` to have it thrown again."
     [& body]
     (if (:ns &env)
       `(cljs.core.async/go
          (try
            ~@body
            (catch cljs.core/ExceptionInfo e#
              e#)
            (catch :default e#
              (ex-info "unknown" {:code :unknown} e#))))
       `(clojure.core.async/go
          (try
            ~@body
            (catch clojure.lang.ExceptionInfo e#
              e#)
            (catch Throwable e#
              (ex-info "unknown" {:code :unknown} e#)))))))

#?(:clj
   (defmacro go-loop
     "Like `core.async/go-loop`, with the error handling of `go`."
     [bindings & body]
     `(jtk-dvlp.async/go
        (loop ~bindings
          ~@body))))

#?(:clj
   (defmacro <!
     "Like `core.async/<!`, but throws the taken value if it is a
      carried error.

      This is what propagates an error up the go block stack: inside a
      `go` the throw is caught again and becomes that block's result,
      so the error keeps climbing until someone catches it. Catch it
      with an ordinary `try`/`catch` on `ExceptionInfo`."
     [?exp]
     (if (:ns &env)
       `(let [v# (cljs.core.async/<! ~?exp)]
          (if (exception? v#)
            (jtk-dvlp.async/athrow v#)
            v#))
       `(let [v# (clojure.core.async/<! ~?exp)]
          (if (exception? v#)
            (jtk-dvlp.async/athrow v#)
            v#)))))

#?(:clj
   (defmacro <!!
     "Like `core.async/<!!`, but throws the taken value if it is a
      carried error. Blocks the calling thread.

      Clojure only — ClojureScript has no blocking take."
     [?exp]
     (if (:ns &env)
       `(throw (js/Error. "Unsupported"))
       `(let [v# (clojure.core.async/<!! ~?exp)]
          (if (exception? v#)
            (jtk-dvlp.async/athrow v#)
            v#)))))

#?(:clj
   (defmacro <?!
     "Like `<!`, but takes a value that may or may not be a channel: a
      channel is taken from, anything else is passed through unchanged.

      For APIs that return either a ready value or a channel, so the
      caller does not have to ask which."
     [sync-or-async-exp]
     `(let [v# ~sync-or-async-exp]
        (if (chan? v#)
          (jtk-dvlp.async/<! v#)
          v#))))

#?(:clj
   (defmacro ^:deprecated <?
     "Deprecated, use `<?!` — it is the same thing under a name that
      says how it relates to `<!` and `<!!`.

      Kept because it is public API and removing it would break
      callers."
     [sync-or-async-exp]
     `(<?! ~sync-or-async-exp)))

#?(:clj
   (defmacro <?!!
     "Like `<!!`, but takes a value that may or may not be a channel.
      Blocks the calling thread.

      Clojure only — ClojureScript has no blocking take."
     [sync-or-async-exp]
     (if (:ns &env)
       `(throw (js/Error. "Unsupported"))
       `(let [v# ~sync-or-async-exp]
          (if (chan? v#)
            (jtk-dvlp.async/<!! v#)
            v#)))))

#?(:clj
   (defn thread-call
     "Like `core.async/thread-call`, with the error handling of `go`:
      an exception from `f` becomes the channel's value instead of
      escaping into the thread pool unnoticed.

      Clojure only."
     ([f]
      (thread-call f :mixed))

     ([f workload]
      (async/thread-call
       (fn []
         (try
           (f)
           (catch clojure.lang.ExceptionInfo e
             e)
           (catch Throwable e
             (ex-info "unknown" {:code :unknown} e))))
       workload))))

#?(:clj
   (defmacro thread
     "Like `core.async/thread`, with the error handling of `go`. Runs
      `body` on a real thread, so it may block.

      Clojure only."
     [& body]
     (if (:ns &env)
       `(throw (js/Error. "Unsupported"))
       `(jtk-dvlp.async/thread-call (^:once fn* [] ~@body) :mixed))))

(defn map
  "Like `core.async/map`, but propagates errors: if any channel in
   `chs` carries an error, or `f` throws, that error becomes the result
   instead of being lost."
  [f chs]
  (async/map
   (fn [& args]
     (try
       (when-let [e (first (filter exception? args))]
         (athrow e))
       (apply f args)
       (catch ExceptionInfo e#
         e#)
       (catch #?(:cljs :default :clj Throwable) e#
         (ex-info "unknown" {:code :unknown} e#))))
   chs))

(defn all
  "Waits for all channels `chs` and yields a vector of their values, in
   the order of `chs`. Alias for `(map vector chs)`.

   Propagates the first error among them."
  [chs]
  (map vector chs))

(defn consume!
  "Calls `f` for every value on channel `ch`. Returns `nil` right away;
   the consuming runs on its own — on a `future` in Clojure, in a go
   block in ClojureScript.

   Ends on a closed channel and on a thrown exception.

   WATCHOUT: It also ends on any falsy value. A `nil` or `false` in the
   stream stops the consuming, even when more values follow."
  [ch f]
  #?(:clj
     (future
       (loop [val (async/<!! ch)]
         (when val
           (f val)
           (recur (async/<!! ch)))))

     :cljs
     (async/go-loop [val (async/<! ch)]
       (when val
         (f val)
         (recur (async/<! ch)))))
  nil)

(defn smap
  "Like `clojure.core/map`, but `<f` is asynchronous and returns a
   channel. Calls happen strictly one after another in the order of
   `xs`, each waiting for the one before it.

   Yields a channel with the vector of results. Propagates errors.

   WATCHOUT: Stops as soon as the next element is falsy — a `nil` or
   `false` in `xs` ends the mapping instead of being passed through.

   See `amap` for the variant that may run in parallel."
  [<f & xs]
  (go-loop [result [], xs xs]
    (if (ffirst xs)
      (let [next-result
            (->> xs
                 (mapv first)
                 (apply <f)
                 (<!))]

        (recur
         (conj result next-result)
         (mapv next xs)))

      result)))

(def chain
  "Alias for `smap`."
  smap)

(defn amap
  "Like `clojure.core/map`, but `<f` is asynchronous and returns a
   channel. Calls may overtake each other and, in Clojure, run in
   parallel; ClojureScript is single-threaded and only interleaves
   them. The *results* keep the order of `xs` either way.

   Yields a channel with the vector of results. Propagates errors.

   See `smap` when the calls must not overlap."
  [<f & xs]
  (->> (apply clojure.core/map <f xs)
       (map vector)))

(defn reduce
  "Like `core.async/reduce`, but propagates errors: an error carried on
   `ch`, or thrown by `f`, ends the reduction and becomes the result."
  [f init ch]
  (async/reduce
   (fn [accu v]
     (try
       (when (exception? v)
         (athrow v))
       (f accu v)
       (catch ExceptionInfo e#
         (reduced e#))
       (catch #?(:cljs :default :clj Throwable) e#
         (reduced (ex-info "unknown" {:code :unknown} e#)))))
   init ch))

(defn areduce
  "Like `clojure.core/reduce`, but `<f` is asynchronous and returns a
   channel. Reduces `coll` into `init`, waiting for each step.

   Yields a channel with the result. Propagates errors.

   WATCHOUT: Stops as soon as the next item is falsy — a `nil` or
   `false` in `coll` ends the reduction."
  [<f init coll]
  (go-loop [accu init, [item & rest-coll] coll]
    (if item
      (recur
       (<! (<f accu item))
       rest-coll)
      accu)))

(defn into
  "Like `core.async/into`, but propagates errors: an error carried on
   `ch` becomes the result instead of ending up in the collection."
  [coll ch]
  (reduce conj coll ch))

(defn awalk
  "Like `clojure.walk/walk`, but `<inner` and `<outer` are
   asynchronous and return channels. Calls may overtake each other and,
   in Clojure, run in parallel.

   Yields a channel with the walked form. Propagates errors.

   Usually reached through `apostwalk` or `aprewalk`."
  [<inner <outer form]
  (go
    (cond
      (list? form)
      (<! (<outer (apply list (<! (amap <inner form)))))

      #?(:cljs (map-entry? form) :clj (instance? clojure.lang.IMapEntry form))
      (do
        (<! (<outer #?(:cljs
                       (MapEntry.
                        (<! (<inner (key form)))
                        (<! (<inner (val form)))
                        nil)

                       :clj
                       (clojure.lang.MapEntry/create
                        (<! (<inner (key form)))
                        (<! (<inner (val form))))))))

      (seq? form)
      (<! (<outer (<! (amap <inner form))))

      (record? form)
      (<! (<outer (<! (areduce (fn [r x] (let [c (async/chan 1)] (async/take! (<inner x) #(conj r %)) c)) form form))))

      (coll? form)
      (<! (<outer (clojure.core/into (empty form) (<! (amap <inner form)))))

      :else
      (<! (<outer form)))))

(defn apostwalk
  "Like `clojure.walk/postwalk`, but `<f` is asynchronous and returns
   a channel. Visits every node of `form` innermost first, so `<f` sees
   a node only after its children were replaced.

   Calls may overtake each other and, in Clojure, run in parallel.
   Yields a channel with the walked form. Propagates errors."
  [<f form]
  (awalk (partial apostwalk <f) <f form))

(defn aprewalk
  "Like `clojure.walk/prewalk`, but `<f` is asynchronous and returns a
   channel. Visits every node of `form` outermost first, so whatever
   `<f` puts in place of a node is walked as well.

   Calls may overtake each other and, in Clojure, run in parallel.
   Yields a channel with the walked form. Propagates errors."
  [<f form]
  (go
    (<!
     (awalk
      (partial aprewalk <f)
      #(let [c (async/chan 1)]
         (async/put! c (identity %))
         c)
      (<! (<f form))))))
