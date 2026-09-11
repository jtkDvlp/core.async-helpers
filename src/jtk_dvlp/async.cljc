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

   Everything in this namespace propagates errors that way, and an
   exception travels as itself: the class, message and `ex-data` that
   were thrown are the ones caught. Only a thrown value that is no
   exception at all — which ClojureScript allows — is lifted into an
   `ExceptionInfo` carrying `{:code :unknown, :error x}`.

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
      [clojure.lang MapEntry]
      [clojure.core.async.impl.channels ManyToManyChannel]))

  ,,,)


(defn chan?
  "Is `x` a `core.async` channel?"
  [x]
  (instance? ManyToManyChannel x))

(defn exception?
  "Is `x` a carried error, i.e. anything the platform can throw?

   On the JVM a `Throwable`, in ClojureScript a `js/Error`. Everything
   throwable travels as itself, so this is the same question as \"may
   `<!` throw this again?\".

   WATCHOUT: This is also the reason an exception cannot be a payload.
   A value that happens to be an exception object is read as an error
   here, not as a result. Wrap it if you mean it as data."
  [x]
  (instance? #?(:clj Throwable :cljs js/Error) x))

(defn ->exception
  "Turns `x` into something that can travel a channel as an error.

   Anything throwable is handed back untouched — the caller built it
   and means it. Anything else is lifted into an `ExceptionInfo` with
   `code` and the value under `:error`.

   WATCHOUT: That second case is why this exists, and it is not a JVM
   concern. JavaScript lets you `throw 42`, and a promise may reject
   with anything at all. Such a value must be lifted, or it would
   arrive on the channel indistinguishable from a result and the error
   would vanish without a sound.

   NOTE: The lifted value goes under `:error` in the `ex-data`, not
   into the `cause`. `clojure.core/ex-info` demands a `Throwable`
   there and throws a `ClassCastException` on anything else, while
   ClojureScript takes whatever it is given — the same code blew up on
   one platform and quietly worked on the other."
  [message code x]
  (if (exception? x)
    x
    (ex-info message {:code code, :error x})))

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

      The exception travels as itself — same class, same message, same
      `ex-data`. Only a thrown value that is not an exception at all is
      lifted into one, which ClojureScript allows. Take the result with
      `<!` to have it thrown again."
     [& body]
     (if (:ns &env)
       `(cljs.core.async/go
          (try
            ~@body
            (catch :default e#
              (jtk-dvlp.async/->exception "unknown" :unknown e#))))
       `(clojure.core.async/go
          (try
            ~@body
            (catch Throwable e#
              e#))))))

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
      so the error keeps climbing until someone catches it.

      WATCHOUT: Catch what was actually thrown. Since exceptions travel
      as themselves, a `catch ExceptionInfo` no longer sees a foreign
      exception — that needs `Exception` (JVM) or `:default` (cljs)."
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
   (def ^:private thread-call-takes-workload?
     "Does the core.async on the classpath know `thread-call`'s
      workload argument?

      NOTE: The argument routes work to a pool per workload kind and
      arrived only in a later core.async than the one this project
      pins; passing it to an older one ends in an `ArityException` on
      every single call. An older core.async has one pool for
      everything, which is exactly `:mixed` — so leaving the argument
      off there is not a workaround but the same behaviour under the
      only name it has.

      Checked rather than assumed because the version is the
      consumer's to choose: a `:dependencies` entry here is routinely
      overridden downstream."
     (->> (meta #'async/thread-call)
          (:arglists)
          (some #(= 2 (count %)))
          (boolean))))

#?(:clj
   (defn thread-call
     "Like `core.async/thread-call`, with the error handling of `go`:
      an exception from `f` becomes the channel's value instead of
      escaping into the thread pool unnoticed.

      Clojure only."
     ([f]
      (thread-call f :mixed))

     ([f workload]
      (let [carry-exception
            (fn []
              (try
                (f)
                (catch Throwable e
                  e)))]

        (if thread-call-takes-workload?
          (async/thread-call carry-exception workload)
          (async/thread-call carry-exception))))))

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
   instead of being lost.

   With no channels at all the result is `(f)`, the same answer
   `clojure.core` gives for folding over nothing — `(map + [])` yields
   `0`, `(map vector [])` yields `[]`."
  [f chs]
  ;; NOTE: A deliberate deviation from `core.async/map`, which waits
  ;;       forever on an empty list of channels: it counts down the
  ;;       channels it has, and with none it never reaches zero and
  ;;       never closes its output. Nobody wants that answer, and it
  ;;       propagates — `all` is this function, and so is `amap`, so an
  ;;       empty collection took the caller down with it.
  ;;
  ;;       `(f)` may of course throw for a function without a 0-arity;
  ;;       `go` then carries that error, which is still an answer rather
  ;;       than a standstill.
  (if (empty? chs)
    (go (f))
    (async/map
     (fn [& args]
       (try
         (when-let [e (first (filter exception? args))]
           (athrow e))
         (apply f args)
         (catch #?(:cljs :default :clj Throwable) e#
           (->exception "unknown" :unknown e#))))
     chs)))

(defn all
  "Waits for all channels `chs` and yields a vector of their values, in
   the order of `chs`. Alias for `(map vector chs)`.

   Propagates the first error among them. With no channels the result
   is `[]`."
  [chs]
  (map vector chs))

(defn consume!
  "Calls `f` for every value on channel `ch`. Returns `nil` right away;
   the consuming runs on its own — on a `future` in Clojure, in a go
   block in ClojureScript.

   Ends on a closed channel and on a thrown exception. A `false` in
   the stream is an ordinary value and is passed to `f` like any
   other."
  [ch f]
  ;; NOTE: `some?`, not truthiness. A closed channel yields `nil` and
  ;;       that is the only thing meant to end the loop — testing the
  ;;       value itself would make a `false` in the stream look like the
  ;;       end of it.
  #?(:clj
     (future
       (loop [val (async/<!! ch)]
         (when (some? val)
           (f val)
           (recur (async/<!! ch)))))

     :cljs
     (async/go-loop [val (async/<! ch)]
       (when (some? val)
         (f val)
         (recur (async/<! ch)))))
  nil)

(defn smap
  "Like `clojure.core/map`, but `<f` is asynchronous and returns a
   channel. Calls happen strictly one after another in the order of
   `xs`, each waiting for the one before it.

   Yields a channel with the vector of results. Propagates errors.
   Stops when the shortest collection runs out, like
   `clojure.core/map`; a `nil` or `false` among the elements is an
   ordinary value.

   See `amap` for the variant that may run in parallel."
  [<f & xs]
  ;; NOTE: The loop ends when a collection runs out, not when an
  ;;       element is falsy. Asking `(ffirst xs)` instead would make a
  ;;       `nil` or `false` in the middle look like the end of the
  ;;       collection and silently drop the rest.
  (go-loop [result [], xs (mapv seq xs)]
    (if (and (seq xs) (every? some? xs))
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

   Yields a channel with the vector of results. Propagates errors. A
   `nil` among the results is an ordinary value and keeps its place.

   See `smap` when the calls must not overlap."
  [<f & xs]
  ;; NOTE: Each result is boxed in a vector before it goes through
  ;;       `map`, and unboxed afterwards. Without that a single `nil`
  ;;       result would take the whole call down with it: a go block
  ;;       whose body yields `nil` closes its channel without ever
  ;;       putting anything on it, and `core.async/map` cannot tell that
  ;;       apart from a channel that is simply done — so it closes its
  ;;       own output and `amap` yields `nil` instead of a vector.
  ;;
  ;;       `map` and `all` keep the plain behaviour on purpose: they
  ;;       take *channels*, where `nil` really does mean "closed". Here
  ;;       the input is a collection, and `nil` in it is data.
  (let [<box-result
        (fn [ch]
          (go [(<! ch)]))]

    (go
      (->> (apply clojure.core/map <f xs)
           (clojure.core/map <box-result)
           (map vector)
           (<!)
           (mapv first)))))

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
       (catch #?(:cljs :default :clj Throwable) e#
         (reduced (->exception "unknown" :unknown e#)))))
   init ch))

(defn areduce
  "Like `clojure.core/reduce`, but `<f` is asynchronous and returns a
   channel. Reduces `coll` into `init`, waiting for each step.

   Yields a channel with the result. Propagates errors. A `nil` or
   `false` in `coll` is an ordinary item and is reduced like any
   other."
  [<f init coll]
  ;; NOTE: Walking the seq rather than testing the item. Asking `(if
  ;;       item ...)` instead would end the reduction at the first
  ;;       `nil` or `false` in `coll`.
  (go-loop [accu init, coll (seq coll)]
    (if coll
      (recur
       (<! (<f accu (first coll)))
       (next coll))
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
      ;; NOTE: The way `clojure.walk/walk` does it for records: reduce
      ;;       over the entries and hang each walked one back onto the
      ;;       record. The record stays its own starting value so that
      ;;       its type survives — an `(empty form)` would be a plain
      ;;       empty map.
      (let [<walk-entry
            (fn [record entry]
              (go
                (conj record (<! (<inner entry)))))]

        (<! (<outer (<! (areduce <walk-entry form form)))))

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
