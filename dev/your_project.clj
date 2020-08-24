(ns your-project
  (:require [jtk-dvlp.async :as a]))


(defn ?do-some-async-stuff
  [& args]
  (a/go*
   (Thread/sleep 1000)
   (let [result
         {:thread-id (.getId (Thread/currentThread))
          :call-args args}]

     (println result)
     result)))

(defn ?fail-during-some-async-stuff
  [& args]
  (a/go*
   (Thread/sleep 1000)
   (->> {:thread-id (.getId (Thread/currentThread))
         :call-args args}
        (ex-info "you got a bug")
        (throw))))

(defn ?do-some-more-stuff
  []
  (a/go*
   (let [a
         (a/<e! (?do-some-async-stuff :a))

         b
         (a/<e! (?fail-during-some-async-stuff :b))

         c
         (a/<e! (?do-some-async-stuff :c))]

     [a b c])))

(comment
  (a/go*
   (try
     (->> (?do-some-more-stuff)
          (a/<e!)
          (println "success"))
     (catch clojure.lang.ExceptionInfo e
       (println "there is an error" e)))))
