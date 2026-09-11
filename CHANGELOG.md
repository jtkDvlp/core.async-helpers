# Changelog

All notable changes to this project are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

`Unreleased` is what sits on the default branch but is not on Clojars yet — so
what you read on GitHub and what `[jtk-dvlp/core.async-helpers "3.6.1"]` gives
you are not the same thing. The per-version documentation lives on
[cljdoc](https://cljdoc.org/d/jtk-dvlp/core.async-helpers/CURRENT).

## [Unreleased]

### Changed — breaking

- **An exception now travels as itself.** Up to 3.x everything that was not
  already an `ExceptionInfo` was converted into one carrying
  `{:code :unknown}`, with the original as its `cause`. A `RuntimeException`
  or a plain `js/Error` is now carried and rethrown unchanged — same class,
  same message, same `ex-data`.

  **What breaks:** `(catch ExceptionInfo e …)` was the documented pattern and
  no longer sees a foreign exception. It needs `Exception` on the JVM and
  `:default` in ClojureScript. The break is a quiet one — the error does not
  disappear, it climbs past the `catch` to the next one, or out of the
  program. Anyone catching `ExceptionInfo` should go through their handlers
  before upgrading.

  `(ex-data e)` is `nil` for such an exception; the `{:code …}` convention
  now only holds for what this library builds itself. This applies wherever
  an error enters: `go`, `go-loop`, `thread`, `thread-call`, `map`, `reduce`,
  `cb->c` and the promise interop.

  A thrown value that is *no* exception at all — ClojureScript lets you
  `throw 42`, and a promise may reject with anything — is still lifted into
  an `ExceptionInfo`, now with the value under `:error`. Without that it
  would arrive on the channel indistinguishable from a result.

- **`Error` is no longer caught on the JVM.** A `StackOverflowError` or
  `OutOfMemoryError` used to become an ordinary channel value, letting the
  program carry on as if it could. It now escapes into core.async's thread,
  which closes the channel. Catching is `Exception` in `go`, `thread-call`
  and `cb->c`. ClojureScript has no such distinction.

- `exception?` answers whether a value is a carried error, and that is now
  anything throwable — a `Throwable` on the JVM, a `js/Error` in
  ClojureScript. It used to accept `ExceptionInfo` only.

- `->exception` hands anything throwable back untouched instead of making it
  the `cause` of a fresh `ExceptionInfo`.

### Fixed

- `thread` and `thread-call` were unusable and always threw an
  `ArityException`: both passed a workload argument to
  `core.async/thread-call`, which the pinned core.async 1.3.610 does not
  accept. The arity is now checked at load time and the argument only passed
  on where it is supported.
- Rejecting with a plain value threw a `ClassCastException` on the JVM.
  `(reject :bad)` reached `ex-info` in the cause position, where Clojure
  demands a `Throwable`. Affected `promise-chan`, `p->c` and `cb->c`. In
  `cb->c` the outer handler swallowed it, so `{:code :callback-error}`
  silently came out as `{:code :callback-based-function-error}`.
- `awalk`, and with it `apostwalk` and `aprewalk`, hung forever on a record.
  The record branch never put anything onto the channel it then waited for.
- `consume!`, `smap` and `areduce` stopped at the first falsy value, so a
  `nil` or `false` in the middle silently dropped everything after it.
- `amap` yielded `nil` instead of a vector as soon as one result was `nil`.
- `map`, and through it `all` and `amap`, waited forever on an empty
  collection.

### Changed

- A rejection carrying a plain value now keeps that value under `:error` in
  the `ex-data`. **This changes ClojureScript**, where it used to sit under
  `ex-cause` — on the JVM it could not be recovered at all. Both platforms now
  behave the same.
- `map` with no channels yields `(f)` rather than waiting forever, so
  `(all [])` is `[]` and `(map + [])` is `0`. A deliberate deviation from
  `core.async/map`.

### Added

- `->exception`. Public because the expansion of `cb->c` runs in the caller's
  namespace.
- A test suite covering every public function and macro on both platforms,
  running in CI on every push and pull request.

## [3.6.1] - 2026-08-31

### Fixed

- A ClojureScript compilation issue.
- README and documentation corrections.

## [3.6.0] - 2026-08-17

### Added

- Stack traces now reach across the go block boundary. A failure shows both
  the block that failed and the one that asked for the value, separated by an
  `ASYNC_BOUNDARY` marker ([#5]).

### Fixed

- `cb->c` and the promise interop pass an `ExceptionInfo` rejection through
  unchanged instead of wrapping it again, so its message and `ex-data`
  survive ([#4]).

## [3.5.1] - 2026-08-13

### Fixed

- `p->c` no longer blocks the calling thread on the JVM ([#3]).

## [3.5.0] - 2026-07-01

### Added

- `thread` and `thread-call`, with the error handling of `go`.

### Deprecated

- `<?` — use `<?!`, which says how it relates to `<!` and `<!!`.

## [3.4.0] - 2025-12-16

### Added

- Blocking takes for use outside a go block: `<!!`, `<?!!` and `<p!!`, plus
  `<?!` for a value that may or may not be a channel.

## [3.3.2] - 2025-12-05

### Fixed

- Corrections in `jtk-dvlp.async`.

---

Releases before 3.3.2 are not reconstructed here; see the
[git history](https://github.com/jtkDvlp/core.async-helpers/commits/master) and
the [tags](https://github.com/jtkDvlp/core.async-helpers/tags).

[#3]: https://github.com/jtkDvlp/core.async-helpers/issues/3
[#4]: https://github.com/jtkDvlp/core.async-helpers/issues/4
[#5]: https://github.com/jtkDvlp/core.async-helpers/issues/5
[Unreleased]: https://github.com/jtkDvlp/core.async-helpers/compare/3.6.1...master
[3.6.1]: https://github.com/jtkDvlp/core.async-helpers/compare/3.6.0...3.6.1
[3.6.0]: https://github.com/jtkDvlp/core.async-helpers/compare/3.5.1...3.6.0
[3.5.1]: https://github.com/jtkDvlp/core.async-helpers/compare/3.5.0...3.5.1
[3.5.0]: https://github.com/jtkDvlp/core.async-helpers/compare/3.4.0...3.5.0
[3.4.0]: https://github.com/jtkDvlp/core.async-helpers/compare/3.3.2...3.4.0
[3.3.2]: https://github.com/jtkDvlp/core.async-helpers/compare/3.3.1...3.3.2
