(ns jtk-dvlp.async
  (:refer-clojure
   :exclude [map])

  #?(:cljs
     (:require-macros
      [jtk-dvlp.async :refer [go* <e! <p! <cb!]]))

  #?(:clj
     (:require
      [clojure.core.async :as async]
      [jtk-dvlp.async.interop.promise]
      [jtk-dvlp.async.interop.callback])

     :cljs
     (:require
      [clojure.core.async :as async]
      [jtk-dvlp.async.interop.promise]
      [jtk-dvlp.async.interop.callback]))

  #?(:clj
     (:import
      [clojure.lang ExceptionInfo]))

  ,,,)

#?(:clj
   (defmacro go*
     "Like `go` but carries thrown `ExceptionInfo` as result.

      Usage in combination with `<e!`, `<p!` and `<cb!`."
     [& body]
     (if (:ns &env)
       `(cljs.core.async/go
          (try
            ~@body
            (catch cljs.core/ExceptionInfo e#
              e#)
            (catch js/Error e#
              (ex-info "error" {:error :unknown} e#))))
       `(clojure.core.async/go
          (try
            ~@body
            (catch clojure.lang.ExceptionInfo e#
              e#)
            (catch Throwable e#
              (ex-info "error" {:error :unknown} e#)))))))

#?(:clj
   (defmacro <e!
     "Like `<!` but tests taken val instance of `ExceptionInfo`, if so throws it.

      Usage in combination with `go*`."
     [?exp]
     (if (:ns &env)
       `(let [v# (cljs.core.async/<! ~?exp)]
          (if (instance? cljs.core/ExceptionInfo v#)
            (throw v#)
            v#))
       `(let [v# (clojure.core.async/<! ~?exp)]
          (if (instance? clojure.lang.ExceptionInfo v#)
            (throw v#)
            v#)))))

(defn map
  "Like `map` but carries thrown `ExceptionInfo` as result.

   Usage in combination with `<e!`."
  [f chs]
  (async/map
   (fn [& args]
     (try
       (apply f args)
       (catch ExceptionInfo e
         e)
       (catch #?(:clj Throwable :cljs js/Error) e
         (ex-info "error" {:error :unknown} e))))
   chs))

(defn promise-chan
  "Creates an promise like channel, see `core.async/promise-chan`.

   Given function `f` can be used to fill the promise.
   `f` will be called with one arg functions `resolve` and `reject`
   to resolve or reject the created promise. Rejection value will
   will be used as `ex-info` `cause`."
  ([]
   (async/promise-chan))

  ([f]
   (let [c (async/promise-chan)

         put-resolution!
         (partial async/put! c)

         put-rejection!
         #(->> %
               (ex-info "promise error" {:error :promise-error})
               (async/put! c))]

     (f put-resolution! put-rejection!)
     c)))

(defn ->promise-chan
  "Ensure given channel `c` to be a `promise-chan` via
   `pipe` it into a new `promise-chan`. See `core.async/promise-chan`
   for more infos."
  [c]
  (->> (async/promise-chan)
       (async/pipe c)))

#?(:clj
   (defmacro <p!
     "Like `<e!` for promise via `p->c` convertion.

      Usage in combination with `go*`."
     [?exp]
     `(<e! (jtk-dvlp.async.interop.promise/p->c ~?exp))))

#?(:clj
   (defmacro <cb!
     "Like `<e!` for callback based functions via `cb->c` convertion.

      Usage in combination with `go*`."
     [?exp]
     `(<e! (jtk-dvlp.async.interop.callback/cb->c ~?exp))))
