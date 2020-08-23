(ns jtk-dvlp.async.interop.promise
  #?(:clj
     (:require
      [clojure.core.async :as async])

     :cljs
     (:require
      [clojure.core.async :as async]))

  #?(:clj
     (:import
      [clojure.lang ExceptionInfo])))


(defn p->c
  "Creates a `promise-chan` and put the val of resolved promise `p`
   or put an instance of `ExceptionInfo` if promise is rejected.
   Closes the channel after took val."
  [p]
  (let [c (async/promise-chan)

        put-val!
        (fn [val]
          (if (nil? val)
            (async/close! c)
            (async/put! c val)))

        forward-error!
        (fn [err]
          (->> err
               (ex-info "promise error" {:error :promise-error})
               (async/put! c)))]

    #?(:clj
       (try
         (put-val! @p)
         (catch Throwable e
           (forward-error! e)))

       :cljs
       (.then p put-val! forward-error!))
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
  "Creates a promise and resolves it with the val of channel `c`
   taken by `<!`, excepted val is an instance of `ExceptionInfo` rejects the
   promise. Closes the channel after took val."
  [c]
  (create-promise
   (fn [resolve reject]
     (async/go
       (let [v (async/<! c)]
         (async/close! c)
         (if (instance? ExceptionInfo v)
           (reject v)
           (resolve v)))))))
