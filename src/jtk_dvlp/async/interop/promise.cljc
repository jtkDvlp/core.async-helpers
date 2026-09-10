(ns jtk-dvlp.async.interop.promise
  "Bridges promises and channels, in both directions, with the error
   propagation of `jtk-dvlp.async`.

   A rejected promise arrives on the channel as a carried error and is
   thrown by `<!`; an error carried on a channel rejects the promise it
   is turned into. So a `.then`/`.catch` chain and a go block stack can
   be mixed without either side losing an error.

   WATCHOUT: `promise` means different things on the two platforms. In
   ClojureScript it is a `js/Promise` with `then` and `catch`. On the
   JVM it is a `clojure.core/promise`, which has neither and only takes
   a single `deliver` — there is no rejection there, only a delivered
   exception value. The functions here even that out; where it still
   shows through, it is said so."

  #?(:cljs
     (:require-macros
      [jtk-dvlp.async.interop.promise :refer [promise-go <p!]]))

  #?(:clj
     (:require
      [clojure.core.async :as async]
      [jtk-dvlp.async])

     :cljs
     (:require
      [cljs.core.async :as async]
      [jtk-dvlp.async])))


(defn- put-n-close!
  [c v]
  (when (some? v)
    (async/put! c v))
  (async/close! c))

(defn- forward-value!
  [c v]
  (put-n-close! c v))

(defn- forward-error!
  [c e]
  (cond->> e
    (not (jtk-dvlp.async/exception? e))
    (ex-info "promise error" {:code :promise-error})

    :always
    (put-n-close! c)))

(defn p->c
  "Turns promise `p` into a `promise-chan` carrying its value, or an
   `ExceptionInfo` if the promise is rejected. A rejection that already
   is an `ExceptionInfo` keeps its message and data.

   WATCHOUT: The channel is closed once the value is on it, but it is a
   `promise-chan` — it hands out its buffered value on every take, also
   when closed. Closed here means \"takes nothing new\", not \"yields
   nothing more\"."
  [p]
  (let [c (async/promise-chan)

        forward-value!
        (partial put-n-close! c)

        forward-error!
        (partial forward-error! c)]

    #?(:clj
       (try
         ;; WATCHOUT: There is no `then`-like api for `promise`.
         ;;           To not blocking the calling thread spwan
         ;;           a new thread / future waiting for delivery.
         (future (forward-value! @p))
         (catch Throwable e
           (forward-error! e)))

       :cljs
       (js-invoke p "then" forward-value! forward-error!))
    c))

(def ^:private create-promise
  #?(:clj
     (fn [f]
       (let [p (promise)]

         (f (partial deliver p) (partial deliver p))
         p))

     :cljs
     #(new js/Promise %)))

(defn c->p
  "Turns channel `c` into a promise: resolved with the channel's value,
   rejected with it if it is a carried error. Closes `c` afterwards.

   The counterpart of `p->c`, for handing a channel to code that
   expects a promise."
  [c]
  (create-promise
   (fn [resolve reject]
     (async/take!
      c
      (fn [v]
        (async/close! c)
        (if (jtk-dvlp.async/exception? v)
          (reject v)
          (resolve v)))))))

(defn promise-chan
  "Creates a `promise-chan` — a channel that keeps its first value and
   hands it out on every take. See `core.async/promise-chan`.

   Without arguments it is just that, empty. Given `f`, it is called
   with two one-argument functions, `resolve` and `reject`, to fill the
   channel — the same shape as a JavaScript promise executor. A
   rejection value that is not already an `ExceptionInfo` is wrapped
   into one as its cause."
  ([]
   (async/promise-chan))

  ([f]
   (let [p (async/promise-chan)

         put-resolution!
         (partial forward-value! p)

         put-rejection!
         (partial forward-error! p)]

     (f put-resolution! put-rejection!)
     p)))

(defn ->promise-chan
  "Ensures channel `c` behaves like a `promise-chan`: its first value
   is kept and handed out on every take, instead of being gone after
   the first one. Closes `c` afterwards.

   For a result several readers need, or one that is read more than
   once."
  [c]
  (let [p (async/promise-chan)]
    (async/take!
     c
     (fn [v]
       (async/close! c)
       (put-n-close! p v)))
    p))

#?(:clj
   (defmacro promise-go
     "Like `jtk-dvlp.async/go`, but yields a `promise-chan`: the result
      can be taken more than once, instead of being gone after the
      first take."
     [& body]
     `(jtk-dvlp.async.interop.promise/->promise-chan
       (jtk-dvlp.async/go
         ~@body))))

#?(:clj
   (defmacro <p!
     "Like `jtk-dvlp.async/<!`, but for a promise: takes its value, or
      throws if it was rejected. Shorthand for `(<! (p->c ?exp))`."
     [?exp]
     `(jtk-dvlp.async/<!
       (jtk-dvlp.async.interop.promise/p->c
        ~?exp))))

#?(:clj
   (defmacro <p!!
     "Like `jtk-dvlp.async/<!!`, but for a promise. Blocks the calling
      thread.

      Clojure only — ClojureScript has no blocking take."
     [?exp]
     `(jtk-dvlp.async/<!!
       (jtk-dvlp.async.interop.promise/p->c
        ~?exp))))
