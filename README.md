[![Clojars Project](https://img.shields.io/clojars/v/jtk-dvlp/core.async-helpers.svg)](https://clojars.org/jtk-dvlp/core.async-helpers)
[![cljdoc badge](https://cljdoc.org/badge/jtk-dvlp/core.async-helpers)](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT)
[![License](https://img.shields.io/badge/License-EPL%202.0-red.svg)](https://opensource.org/licenses/EPL-2.0)

# Helpers for core.async

Helper pack for [core.async](https://github.com/clojure/core.async) with focus on error propagation, see [docs](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT) for more and examples.

## Features

  * error propagation by climbing up the go block stack via [go*](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT/api/jtk-dvlp.async#go*) and [<e!](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT/api/jtk-dvlp.async#<e!) (all functions within this package propagate errors)
  * promise channel helpers
    * to ensure promise-chan via [->promise-chan](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT/api/jtk-dvlp.async#->promise-chan) and its behavior
    * to create promise-chan via [promise-chan](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT/api/jtk-dvlp.async#promise-chan) function with resolve and reject handlers
    * conversion from channel to promise and vice versa via [c->p](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT/api/jtk-dvlp.async.inertop.promise#c->p), [p->c](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT/api/jtk-dvlp.async.inertop.promise#p->c) and [<!p](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT/api/jtk-dvlp.async#<!p)
  * helpers to handle callback based functions by conversion into channel via [cb->c](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT/api/jtk-dvlp.async.interop.callback#cb->c) and [<cb!](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT/api/jtk-dvlp.async#<cb!)

## Getting started

### Get it / add dependency

Add the following dependency to your `project.clj`:<br>
[![Clojars Project](https://img.shields.io/clojars/v/jtk-dvlp/core.async-helpers.svg)](https://clojars.org/jtk-dvlp/core.async-helpers)

### Usage

```clojure
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
         ;; Change to
         ;; (a/<e! (?do-some-async-stuff :b))
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
```


## Appendix

I´d be thankful to receive patches, comments and constructive criticism.

Hope the package is useful :-)
