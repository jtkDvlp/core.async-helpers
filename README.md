[![CI](https://github.com/jtkDvlp/core.async-helpers/actions/workflows/ci.yml/badge.svg)](https://github.com/jtkDvlp/core.async-helpers/actions/workflows/ci.yml)
[![Clojars Project](https://img.shields.io/clojars/v/jtk-dvlp/core.async-helpers.svg)](https://clojars.org/jtk-dvlp/core.async-helpers)
[![cljdoc badge](https://cljdoc.org/badge/jtk-dvlp/core.async-helpers)](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT)
[![License](https://img.shields.io/badge/License-EPL%202.0-red.svg)](https://opensource.org/licenses/EPL-2.0)
[![paypal](https://www.paypalobjects.com/en_US/i/btn/btn_donate_SM.gif)](https://www.paypal.com/donate?hosted_button_id=2PDXQMHX56T6U)

# Helpers for core.async

Helper pack for [core.async](https://github.com/clojure/core.async) with focus on error propagation. Clojure and ClojureScript, from one `.cljc` source.

See the [API docs](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT) for the full reference.

## The problem it solves

In plain `core.async` an exception thrown inside a `go` block is swallowed. The block's channel just closes, the caller takes `nil`, and nothing says why:

```clojure
;; Clojure REPL, plain core.async
(async/<!! (async/go (throw (ex-info "boom" {}))))
;; => nil
```

Here the exception travels as a *value* on the channel and is thrown again by `<!` in whichever go block takes it. Inside a `go` that throw is caught once more and becomes that block's result — so an error keeps climbing the go block stack until someone catches it, the way it would in synchronous code:

```clojure
;; same REPL, with this package
(a/<!! (a/go (throw (ex-info "boom" {}))))
;; => throws clojure.lang.ExceptionInfo: boom
```

## Features

Full reference per namespace:
[`jtk-dvlp.async`](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT/api/jtk-dvlp.async) ·
[`…interop.promise`](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT/api/jtk-dvlp.async.interop.promise) ·
[`…interop.callback`](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT/api/jtk-dvlp.async.interop.callback) ·
[`…print`](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT/api/jtk-dvlp.async.print)

  * **Error propagation up the go block stack.** Every function in this package propagates errors, not only the obvious ones: `go`, `go-loop`, `<!`, `<!!`, `thread`, and `<?!` for a value that may or may not be a channel.

  * **Stack traces that survive the boundary.** The trace is stitched across the go block boundary at an `ASYNC_BOUNDARY` marker, so it shows both the block that failed and the one that asked for the value — instead of ending where the thread began.

  * **Collection helpers.** `map`, `all`, `reduce`, `into` and `consume!` over channels; `smap` (strictly sequential), `amap` (may run in parallel), `areduce`, `apostwalk` and `aprewalk` where the mapping function itself is asynchronous.

  * **Promise interop.** `<p!` takes a promise inside a go block; `p->c` and `c->p` convert either way; `promise-go` and `->promise-chan` give a result that can be read more than once; `promise-chan` builds one from `resolve`/`reject` handlers. A rejected promise arrives as a thrown error, and an error on a channel rejects the promise it becomes.

  * **Callback interop.** `cb->c` turns a callback-based call into a channel, and `<cb!` takes from it — so the call reads like any other step in a go block instead of nesting one level deeper, and a failure arrives as a thrown error rather than as a second callback.

  * **Debug printing.** `<println` and `<pprint` print what a channel ends up with — the value, or the error it carries.

## Getting started

### Add the dependency

[![Clojars Project](https://img.shields.io/clojars/v/jtk-dvlp/core.async-helpers.svg)](https://clojars.org/jtk-dvlp/core.async-helpers)

### Do not mix with `clojure.core.async`

Error propagation works because the error is an ordinary value on the channel. A plain `core.async/<!` in between takes that value silently — the error is gone, and nothing is left to notice it by. The same goes for a `core.async/go` block inside a stack of propagating ones: it does not carry the error on, and the chain breaks there.

So within a go block stack that should propagate, take with `jtk-dvlp.async/<!` and open blocks with `jtk-dvlp.async/go`. `timeout`, `chan`, `put!` and the rest of `core.async` are fine — they do not touch the value.

### Usage

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

The `catch` sees the error from `<fail-during-some-async-stuff` even though it was thrown in a different go block, on a different thread. `<do-some-async-stuff :c` is never reached, just as it would not be in synchronous code.

**Catch what was actually thrown.** An exception travels as itself — the class, message and `ex-data` that were thrown are the ones you catch. The example above catches `ExceptionInfo` because that is what it throws; a foreign exception needs `Exception` on the JVM or `:default` in ClojureScript. Only a thrown value that is no exception at all (ClojureScript lets you `throw 42`) is lifted into an `ExceptionInfo`, with the value under `:error`.

Up to 3.x everything was converted into an `ExceptionInfo` first, so `catch ExceptionInfo` saw every error. If you are upgrading from 3.x, that is the one thing to go through your handlers for — see the [changelog](CHANGELOG.md).

## Development

```bash
lein test                                          # Clojure
lein test-cljs && node target/test-cljs/tests.js   # ClojureScript
```

Both run on every push and pull request, see [`.github/workflows/ci.yml`](.github/workflows/ci.yml).

What has changed, and what is on the default branch but not released yet, is in [`CHANGELOG.md`](CHANGELOG.md).

The tests live in `.cljc` and run on both platforms from one source.

When a bug turns up that is not fixed in the same breath, the test for it stays — marked `^:known-bug`, with a `FIXME:` above it saying what is broken. Such a test spells out the *correct* behaviour, so it fails on purpose and is kept out of the normal run:

```bash
lein test :known-bug   # only those
lein test :all         # everything
```

There are none at the moment.

## Appendix

I´d be thankful to receive patches, comments and constructive criticism.

Hope the package is useful :-)
