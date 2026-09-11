[![Clojars Project](https://img.shields.io/clojars/v/jtk-dvlp/core.async-helpers.svg)](https://clojars.org/jtk-dvlp/core.async-helpers)
[![cljdoc badge](https://cljdoc.org/badge/jtk-dvlp/core.async-helpers)](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT)
[![License](https://img.shields.io/badge/License-EPL%202.0-red.svg)](https://opensource.org/licenses/EPL-2.0)
[![paypal](https://www.paypalobjects.com/en_US/i/btn/btn_donate_SM.gif)](https://www.paypal.com/donate?hosted_button_id=2PDXQMHX56T6U)

# Helpers for core.async

Helper pack for [core.async](https://github.com/clojure/core.async) with focus on error propagation, see [docs](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT) for more and examples.

## Features

  * error propagation by climbing up the go block stack via [go](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT/api/jtk-dvlp.async#go) / [go-loop](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT/api/jtk-dvlp.async#go-loop) / [map](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT/api/jtk-dvlp.async#map) / [reduce](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT/api/jtk-dvlp.async#reduce) / [<!](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT/api/jtk-dvlp.async#<!) / [<?](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT/api/jtk-dvlp.async#<?) and [more](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT/api/jtk-dvlp.async) (all functions within this package propagate errors)
  * stacktrace extending beyond the context of error threads, providing a complete stacktrace and prevent context loss from compaction
  * promise channel helpers
    * to ensure promise-chan via [promise-go](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT/api/jtk-dvlp.async.interop.promise#promise-go) [->promise-chan](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT/api/jtk-dvlp.async.interop.promise#->promise-chan) and its behavior
    * to create promise-chan via [promise-chan](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT/api/jtk-dvlp.async.interop.promise#promise-chan) function with resolve and reject handlers
    * conversion from channel to promise and vice versa via [c->p](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT/api/jtk-dvlp.async.inertop.promise#c->p), [p->c](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT/api/jtk-dvlp.async.inertop.promise#p->c) and [<!p](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT/api/jtk-dvlp.async.interop.promise#<!p)
  * helpers to handle callback based functions by conversion into channel via [cb->c](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT/api/jtk-dvlp.async.interop.callback#cb->c) and [<cb!](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT/api/jtk-dvlp.async.interop.callback#<cb!)

## Getting started

### Get it / add dependency

Add the following dependency to your `project.clj`:<br>
[![Clojars Project](https://img.shields.io/clojars/v/jtk-dvlp/core.async-helpers.svg)](https://clojars.org/jtk-dvlp/core.async-helpers)

### Usage

Pay attention mixing up error propagation functions of this library and clojure.core.async functions. clojure.core.async function do not propagate errors. E.g. using a core.async go-block within a error propagation go-block stack will break error propagation. So do not mix it up!

```clojure
(ns your-project
  #?(:clj
     (:require
      [clojure.core.async :refer [timeout]]
      [jtk-dvlp.async :as a])

     :cljs
     (:require
      [cljs.core.async :refer [timeout]]
      [jtk-dvlp.async :as a]))

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
```


## Contributing

### Commit messages

Commit subjects follow [Conventional
Commits](https://www.conventionalcommits.org/en/v1.0.0/):

```
<type>[(<scope>)][!]: <description>
```

The `!` marks a breaking change and belongs to the type, not to
`feat` — `fix!:` is just as valid and means a bug fix that breaks.

Pull requests are merged, not squashed, so every commit of a branch ends
up on `master` — the convention applies to each of them, not just to the
pull request title. A CI job checks this on every pull request.

The type decides the next version:

| Subject | Release |
|---|---|
| `fix: …` | patch — `3.6.1` → `3.6.2` |
| `feat: …` | minor — `3.6.1` → `3.7.0` |
| any type with a `!`, or a `BREAKING CHANGE:` footer | major — `3.6.1` → `4.0.0` |
| `docs:`, `test:`, `ci:`, `chore:`, `refactor:`, `style:`, `perf:`, `build:`, `revert:` | none on its own |

### Releasing

Releasing is automatic; nobody edits a version number by hand.

1. A merge to `master` lets
   [release-please](https://github.com/googleapis/release-please) open or
   update a release pull request. It carries the next version in
   `project.clj` and the changelog entries derived from the commits since
   the last release.
2. Merging that pull request creates the git tag and the GitHub release.
3. The same workflow run then tests the tagged state and pushes the
   artifact to Clojars.

So the release pull request is the point where a release is decided —
until it is merged, nothing leaves the house.


## Appendix

I´d be thankful to receive patches, comments and constructive criticism.

Hope the package is useful :-)
