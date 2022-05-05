(ns jtk-dvlp.async.print
  #?(:cljs
     (:require-macros
      [jtk-dvlp.async.print :refer [<debug <println <pprint]]))

  #?(:clj
     (:require
      [jtk-dvlp.async]
      [clojure.pprint])

     :cljs
     (:require
      [jtk-dvlp.async]
      [cljs.pprint])))


#?(:clj
   (defmacro <debug
     [print-fn <form]
     `(jtk-dvlp.async/go
        (try
          (~print-fn (jtk-dvlp.async/<! ~<form))
          (catch :default e#
            (~print-fn e#))))))

#?(:clj
   (defmacro <println
     [<form]
     `(<debug println ~<form)))

#?(:clj
   (defmacro <pprint
     [<form]
     (let [pprint
           (if (:ns &env)
             'cljs.pprint/pprint
             'clojure.pprint/pprint)]

       `(<debug ~pprint ~<form))))
