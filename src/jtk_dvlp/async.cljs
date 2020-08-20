(ns jtk-dvlp.async
  (:refer-clojure
   :exclude [map])

  (:require-macros
   [jtk-dvlp.async :refer [<e! go* ?map cb->c <cb! safe]])

  (:require
   [cljs.core.async :refer [go <! close! map]]))


(defn- js-error->ex-info
  [js-error & additions]
  (ex-info
   (.-message js-error)
   (apply hash-map additions)
   (.-stack js-error)))

(defn c->p
  "Creates a promise and resolves it with the val of channel `c`
   taken by `<!`, excepted val is an instance of `ExceptionInfo` rejects the
   promise. Closes the channel after took val."
  [c]
  (->> (fn [resolve reject]
         (go
           (let [v (<! c)]
             (close! c)
             (if (instance? ExceptionInfo v)
               (reject v)
               (resolve v)))))

       (new js/Promise)))

(defn ?map
  "Like `map but carries thrown `ExceptionInfo` as result.

   Usage in combination with `<e!`."
  [f chs]
  (map
   (fn [& args]
     (safe (apply f args)))
   chs))
