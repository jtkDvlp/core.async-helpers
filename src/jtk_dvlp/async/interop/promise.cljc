(ns jtk-dvlp.async.interop.promise
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
   taken by `<!` or rejects it on error / exception. Closes the channel after took val."
  [c]
  (create-promise
   (fn [resolve reject]
     (->> (fn [v]
            (async/close! c)
            (if (jtk-dvlp.async/exception? v)
              (reject v)
              (resolve v)))
          (async/take! c)))))

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
  "Ensure given channel `c` to be a `promise-chan`.
   See `core.async/promise-chan` for more infos.
   Auto closes channel `c`."
  [c]
  (let [p (async/promise-chan)]
    (async/take!
     c (fn [v]
         (when (some? v)
           (async/put! p v))
         (async/close! p)))
    p))

#?(:clj
   (defmacro promise-go
     "Like `go` but returns a `promise-chan`."
     [& body]
     `(jtk-dvlp.async.interop.promise/->promise-chan
       (jtk-dvlp.async/go
         ~@body))))

#?(:clj
   (defmacro <p!
     "Like `<!` for promise via `p->c` convertion."
     [?exp]
     `(jtk-dvlp.async/<!
       (jtk-dvlp.async.interop.promise/p->c
        ~?exp))))
