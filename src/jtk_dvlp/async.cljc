(ns jtk-dvlp.async
  (:refer-clojure
   :exclude [map reduce into])

  #?(:cljs
     (:require-macros
      [jtk-dvlp.async :refer [go go-loop <! <?]]))

  #?(:clj
     (:require
      [clojure.core.async :as async])

     :cljs
     (:require
      [cljs.core.async :as async]
      [cljs.core.async.impl.channels :refer [ManyToManyChannel]]))

  #?(:clj
     (:import
      [clojure.core.async.impl.channels ManyToManyChannel]))

  ,,,)


(defn chan?
  [x]
  (instance? ManyToManyChannel x))

#?(:clj
   (defn exception?
     [x]
     (instance? Throwable x))

   :cljs
   (defn exception?
     [x]
     (some #(instance? % x) [cljs.core.ExceptionInfo js/Error])))

#?(:clj
   (defmacro go
     "Like `core.async/go` but carries thrown error / exception as result."
     [& body]
     (if (:ns &env)
       `(cljs.core.async/go
          (try
            ~@body
            (catch cljs.core/ExceptionInfo e#
              e#)
            (catch js/Error e#
              e#)
            (catch :default e#
              (ex-info "error" {:error :unknown} e#))))
       `(clojure.core.async/go
          (try
            ~@body
            (catch Throwable e#
              e#))))))

#?(:clj
   (defmacro go-loop
     "Like `core.async/go-loop` but carries thrown error / exception as result."
     [bindings & body]
     `(jtk-dvlp.async/go
        (loop ~bindings
          ~@body))))

#?(:clj
   (defmacro <!
     "Like `core.async/<!` but tests taken val of error / exception, if so throws it."
     [?exp]
     (if (:ns &env)
       `(let [v# (cljs.core.async/<! ~?exp)]
          (if (exception? v#)
            (throw v#)
            v#))
       `(let [v# (clojure.core.async/<! ~?exp)]
          (if (exception? v#)
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

(defn map
  "Like `core.async/map` but carries thrown error / exception as result."
  [f chs]
  (async/map
   (fn [& args]
     #?(:clj
        (try
          (when-let [e (first (filter exception? args))]
            (throw e))
          (apply f args)
          (catch Throwable e#
            e#))
        :cljs
        (try
          (when-let [e (first (filter exception? args))]
            (throw e))
          (apply f args)
          (catch cljs.core/ExceptionInfo e#
            e#)
          (catch js/Error e#
            e#)
          (catch :default e#
            (ex-info "error" {:error :unknown} e#)))))
   chs))

(defn reduce
  "Like `core.async/reduce` but carries thrown error / exception as result."
  [f init ch]
  (async/reduce
   (fn [accu v]
     #?(:clj
        (try
          (when (exception? v)
            (throw v))
          (f accu v)
          (catch Throwable e#
            (reduced e#)))
        :cljs
        (try
          (when (exception? v)
            (throw v))
          (f accu v)
          (catch cljs.core/ExceptionInfo e#
            (reduced e#))
          (catch js/Error e#
            (reduced e#))
          (catch :default e#
            (reduced (ex-info "error" {:error :unknown} e#))))))
   init ch))

(defn into
  "Like `core.async/into` but carries thrown error / exception as result."
  [coll ch]
  (reduce conj coll ch))
