(ns jtk-dvlp.async
  (:refer-clojure
   :exclude [map pmap amap areduce reduce into])

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
      [clojure.lang ExceptionInfo MapEntry]
      [clojure.core.async.impl.channels ManyToManyChannel]))

  ,,,)


(defn chan?
  [x]
  (instance? ManyToManyChannel x))

(defn exception?
  [x]
  (instance? ExceptionInfo x))

#?(:clj
   (defmacro go
     "Like `core.async/go` but carries thrown exception (will convert to `ExceptionInfo`) as result."
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
     "Like `core.async/go-loop` but carries thrown exception (will convert to `ExceptionInfo`) as result."
     [bindings & body]
     `(jtk-dvlp.async/go
        (loop ~bindings
          ~@body))))

#?(:clj
   (defmacro <!
     "Like `core.async/<!` but tests taken val of exception (`ExceptionInfo`), if so throws it."
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
   (defmacro <!!
     "Like `core.async/<!!` but tests taken val of exception (`ExceptionInfo`), if so throws it."
     [?exp]
     (if (:ns &env)
       `(throw (js/Error. "Unsupported"))
       `(let [v# (clojure.core.async/<!! ~?exp)]
          (if (exception? v#)
            (throw v#)
            v#)))))

#?(:clj
   (defmacro <?!
     "Like `<!` but can handle channels and non channel values."
     [sync-or-async-exp]
     `(let [v# ~sync-or-async-exp]
        (if (chan? v#)
          (jtk-dvlp.async/<! v#)
          v#))))

#?(:clj
   (defmacro <?
     "Like `<!` but can handle channels and non channel values."
     [sync-or-async-exp]
     `(<?! ~sync-or-async-exp)))

#?(:clj
   (defmacro <?!!
     "Like `<!!` but can handle channels and non channel values."
     [sync-or-async-exp]
     (if (:ns &env)
       `(throw (js/Error. "Unsupported"))
       `(let [v# ~sync-or-async-exp]
          (if (chan? v#)
            (jtk-dvlp.async/<!! v#)
            v#)))))

(defn map
  "Like `core.async/map` but carries thrown exception (will convert to `ExceptionInfo`) as result."
  [f chs]
  (async/map
   (fn [& args]
     (try
       (when-let [e (first (filter exception? args))]
         (throw e))
       (apply f args)
       (catch ExceptionInfo e#
         e#)
       (catch #?(:cljs :default :clj Throwable) e#
         (ex-info "unknown" {:code :unknown} e#))))
   chs))

(defn all
  "Alias for `(map vector chs)` providing an vector of all resolved values."
  [chs]
  (map vector chs))

(defn consume!
  "Consumes channel `ch` executing function `f` for every value on channel. Spawns a new thread for execution on the JVM. Execution will be asynchron. Call returns immediately with `nil`. Thrown exceptions will abort consume."
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
  "Like `clojure.core/map` but given function `<f` is async. Execution of `<f` with values of `xs` will be sequential with the given order of `xs`. Carries thrown exception (will convert to `ExceptionInfo`) as result.

  Also see `amap`"
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
  "Alias for `smap`"
  smap)

(defn amap
  "Like `clojure.core/map` but given function `<f` is async. Execution of `<f` with values of `xs` can be unordered an for clojure (not clojurescript) in parallel. Carries thrown exception (will convert to `ExceptionInfo`) as result.

  Also see `smap`"
  [<f & xs]
  (->> (apply clojure.core/map <f xs)
       (map vector)))

(defn reduce
  "Like `core.async/reduce` but carries thrown exception (will convert to `ExceptionInfo`) as result."
  [f init ch]
  (async/reduce
   (fn [accu v]
     (try
       (when (exception? v)
         (throw v))
       (f accu v)
       (catch ExceptionInfo e#
         (reduced e#))
       (catch #?(:cljs :default :clj Throwable) e#
         (reduced (ex-info "unknown" {:code :unknown} e#)))))
   init ch))

(defn areduce
  "Like `clojure.core/reduce` but given function `<f` is async. Carries thrown exception (will convert to `ExceptionInfo`) as result."
  [<f init coll]
  (go-loop [accu init, [item & rest-coll] coll]
    (if item
      (recur
       (<! (<f accu item))
       rest-coll)
      accu)))

(defn into
  "Like `core.async/into` but carries thrown exception (will convert to `ExceptionInfo`) as result."
  [coll ch]
  (reduce conj coll ch))

(defn awalk
  "Like `clojure.core/walk` but given function `<inner` and `<outer` are async. Execution with values of `form` can be unordered an for clojure (not clojurescript) in parallel. Carries thrown exception (will convert to `ExceptionInfo`) as result."
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
  "Like `clojure.core/postwalk` but given function `<f` is async. Execution with values of `form` can be unordered an for clojure (not clojurescript) in parallel. Carries thrown exception (will convert to `ExceptionInfo`) as result."
  [<f form]
  (awalk (partial apostwalk <f) <f form))

(defn aprewalk
  "Like `clojure.core/prewalk` but given function `<f` is async. Execution with values of `form` can be unordered an for clojure (not clojurescript) in parallel. Carries thrown exception (will convert to `ExceptionInfo`) as result."
  [<f form]
  (go
    (<!
     (awalk
      (partial aprewalk <f)
      #(let [c (async/chan 1)]
         (async/put! c (identity %))
         c)
      (<! (<f form))))))
