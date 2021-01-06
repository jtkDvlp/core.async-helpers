(ns jtk-dvlp.async
  (:refer-clojure
   :exclude [map reduce into])

  #?(:cljs
     (:require-macros
      [jtk-dvlp.async :refer [go go-loop <!]]))

  #?(:clj
     (:require
      [clojure.core.async :as async])

     :cljs
     (:require
      [cljs.core.async :as async]
      [cljs.core.async.impl.channels :refer [ManyToManyChannel]]))

  #?(:clj
     (:import
      [clojure.lang ExceptionInfo]
      [clojure.core.async.impl.channels ManyToManyChannel]))

  ,,,)

(defn chan?
  "Checks `x` of type channel (`clojure.core.async.impl.channels/ManyToManyChannel` / `cljs.core.async.impl.channels/ManyToManyChannel`)."
  [x]
  (instance? ManyToManyChannel x))

#?(:clj
   (defmacro go
     "Like `core.async/go` but carries thrown `ExceptionInfo` as result."
     [& body]
     (if (:ns &env)
       `(cljs.core.async/go
          (try
            ~@body
            (catch cljs.core/ExceptionInfo e#
              e#)
            (catch :default e#
              (ex-info "error" {:error :unknown} e#))))
       `(clojure.core.async/go
          (try
            ~@body
            (catch clojure.lang.ExceptionInfo e#
              e#)
            (catch Throwable e#
              (ex-info "error" {:error :unknown} e#)))))))

#?(:clj
   (defmacro go-loop
     "Like `core.async/go-loop` but carries thrown `ExceptionInfo` as result."
     [bindings & body]
     `(jtk-dvlp.async/go (loop ~bindings ~@body))))

#?(:clj
   (defmacro <!
     "Like `core.async/<!` but tests taken val instance of `ExceptionInfo`, if so throws it."
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

#?(:clj
   (defmacro <?
     "Like `<!` but can handle channels and non channel values."
     [sync-or-async-exp]
     `(let [v# ~sync-or-async-exp]
        (if (chan? v#)
          (jtk-dvlp.async/<! v#)
          v#))))

(def ^:private exception?
  (partial instance? ExceptionInfo))

(defn map
  "Like `core.async/map` but carries thrown `ExceptionInfo` as result."
  [f chs]
  (async/map
   (fn [& args]
     (try
       (when-let [e (first (filter exception? args))]
         (throw e))
       (apply f args)
       (catch ExceptionInfo e
         e)
       (catch #?(:clj Throwable :cljs :default) e
         (ex-info "error" {:error :unknown} e))))
   chs))


(defn reduce
  "Like `core.async/reduce` but carries thrown `ExceptionInfo` as result."
  [f init ch]
  (async/reduce
   (fn [accu v]
     (try
       (when (exception? v)
         (throw v))
       (f accu v)
       (catch ExceptionInfo e
         (reduced e))
       (catch #?(:clj Throwable :cljs :default) e
         (reduced (ex-info "error" {:error :unknown} e)))))
   init ch))

(defn into
  "Like `core.async/into` but carries thrown `ExceptionInfo` as result."
  [coll ch]
  (reduce conj coll ch))
