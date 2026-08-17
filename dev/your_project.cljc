(ns your-project
  #?(:clj
     (:require
      [clojure.core.async :refer [timeout]]
      [jtk-dvlp.async :as a]
      [jtk-dvlp.async.print :as p])

     :cljs
     (:require
      [cljs.core.async :refer [timeout]]
      [jtk-dvlp.async :as a]
      [jtk-dvlp.async.print :as p]))

  #?(:clj
     (:import
      [clojure.lang ExceptionInfo]))

  ,,,)


(defn <do-some-async-stuff
  [& args]
  (a/go
    (a/<! (timeout 1000))
    (let [result
          {:call-args args}]

      (println result)
      result)))

(defn <fail-during-some-async-stuff
  [& args]
  (a/go
    (a/<! (timeout 1000))
    (->> {:call-args args}
         (ex-info "you got a bug")
         (throw))))

(comment

  (defn <error
    []
    (a/go
      (throw (ex-info "AHHH!" {:nix :da}))))

  (defn <c
    []
    (a/go
      (conj (a/<! (<error))
            :c)))

  (defn <b
    []
    (a/go
      (conj (a/<! (<c))
            :b)))

  (defn <a
    []
    (a/go
      (conj (a/<! (<b))
            :a)))

  (p/<pprint
   (<a))


  (a/go
    (try
      (let [a
            (a/<! (<do-some-async-stuff :a))

            b
            (a/<! (<fail-during-some-async-stuff :b))

            c
            (a/<! (<do-some-async-stuff :c))]

        (println [a b c]))

      (catch ExceptionInfo e
        (println "there is an error" e))))

  ,,,)
