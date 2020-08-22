(ns jtk-dvlp.async
  (:refer-clojure
   :exclude [map])

  #?(:cljs
     (:require-macros
      [jtk-dvlp.async :refer [go* <e! safe]]))

  #?(:clj
     (:require
      [clojure.core.async :as async]
      [jtk-dvlp.async.interop.promise :as p])

     :cljs
     (:require
      [clojure.core.async :as async]
      [jtk-dvlp.async.interop.promise :as p])))


#?(:clj
   (defmacro ^:private safe
     [& body]
     (if (:ns &env)
       `(try
          ~@body
          (catch cljs.core/ExceptionInfo e#
            e#)
          (catch js/Error e#
            (ex-info
             (.-message e#)
             {:error :unknown}
             (.-stack e#))))
       `(try
          ~@body
          (catch clojure.lang.ExceptionInfo e#
            e#)
          (catch Throwable e#
            (ex-info "error" {:error :unknown} e#))))))

#?(:clj
   (defmacro go*
     "Like `go` but carries thrown `ExceptionInfo` as result.

      Usage in combination with `<e!`, `<p!` and `<cb!`."
     [& body]
     (if (:ns &env)
       `(cljs.core.async/go
          (safe ~@body))
       `(clojure.core.async/go
          (safe ~@body)))))

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
  "Like `map but carries thrown `ExceptionInfo` as result.

   Usage in combination with `<e!`."
  [f chs]
  (async/map
   (fn [& args]
     (safe (apply f args)))
   chs))

(defn promise-chan
  "TODO"
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
  [c]
  (->> (async/promise-chan)
       (async/pipe c)))

#?(:clj
   (defmacro <p!
     "Like `<!` for a promise via `p->c`."
     [?exp]
     `(<e! (p/p->c ~?exp))))
