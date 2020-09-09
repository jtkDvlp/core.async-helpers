(ns your-project
  #?(:clj
     (:require
      [clojure.core.async :refer [timeout]]
      [jtk-dvlp.async :as a])

     :cljs
     (:require
      [cljs.core.async :refer [timeout]]
      [jtk-dvlp.async :as a])))


(defn ?do-some-async-stuff
  [& args]
  (a/go
    (a/<! (timeout 1000))
    (let [result
          {:call-args args}]

      (println result)
      result)))

(defn ?fail-during-some-async-stuff
  [& args]
  (a/go
    (a/<! (timeout 1000))
    (->> {:call-args args}
         (ex-info "you got a bug")
         (throw))))

(defn ?do-some-more-stuff
  []
  (a/go
    (let [a
          (a/<! (?do-some-async-stuff :a))

          b
          (a/<! (?fail-during-some-async-stuff :b))

          c
          (a/<! (?do-some-async-stuff :c))]

      [a b c])))

(comment
  (a/go
    (try
      (->> (?do-some-more-stuff)
           (a/<!)
           (println "success"))
      (catch #?(:clj clojure.lang.ExceptionInfo
                :cljs ExceptionInfo) e
        (println "there is an error" e)))))
