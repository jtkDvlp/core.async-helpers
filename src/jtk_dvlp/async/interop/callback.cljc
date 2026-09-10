(ns jtk-dvlp.async.interop.callback
  "Turns callback-based functions into channels, with the error
   propagation of `jtk-dvlp.async`.

   A callback API does not fit into a sequence of steps: the rest of
   the work has to move inside the callback, and every further call
   nests one level deeper. `cb->c` turns such a call into a channel, so
   it reads like any other step in a go block — and a failure arrives
   as a thrown error rather than as a second callback.

   WATCHOUT: `cb->c` is a macro that *rewrites* the expression handed
   to it. It looks for the symbols `callback`, `resolve` and `reject`
   inside and puts its own functions in their place. Those symbols are
   therefore written bare, without ever being defined — and they are
   only found where they literally stand. Move a callback into a helper
   function and the macro cannot see it any more."

  #?(:cljs
     (:require-macros
      [jtk-dvlp.async.interop.callback :refer [cb->c <cb!]]))

  #?(:clj
     (:require
      [clojure.walk :refer [postwalk]]
      [clojure.core.async])

     :cljs
     (:require
      [cljs.core.async])))


#?(:clj
   (defn- replace-symbol
     [search replacement x]
     (if (= x search)
       replacement
       x)))

#?(:clj
   (defmacro cb->c
     "Creates a channel from the callbacks of `exp`.

      The symbols `callback`, `resolve` and `reject` mark the callback
      positions in `exp`; whatever they are called with is put onto the
      new channel — `reject` as a carried error. Without any mark, the
      callback is assumed to be the last argument of `exp` and is
      appended.

      The callbacks of `exp` must take exactly one argument.

      A rejection that is not already an `ExceptionInfo` is wrapped
      into one with `{:code :callback-error}` as its cause. If calling
      `exp` itself throws, that error goes onto the channel too, and
      the channel is closed.

      With `auto-close?` (the default) the channel is closed after the
      first put, whether resolution or rejection — right for a call
      that answers once. Pass `false` for a source that calls back
      repeatedly; then closing is up to the caller.

      WATCHOUT: The marks are found by walking `exp` for those literal
      symbols. A callback that lives in a helper function instead of
      standing inline is invisible to the macro — which is why the
      `reject` below sits in an inline `fn`.

      Example:
      ```clojure
      (require '[jtk-dvlp.async :as a])

      (defn read-file
        [path on-success on-failure]
        ,,,)

      (a/go
        (try
          (println
           (<cb!
            (read-file
             \"/etc/hosts\"
             resolve
             ;; Inline, so that `cb->c` can see the `reject` mark.
             (fn add-context-before-rejecting [error]
               (->> {:code :read-failed, :path \"/etc/hosts\"}
                    (ex-info \"could not read file\")
                    (reject))))))

          (catch ExceptionInfo e
            (println \"failed:\" (ex-message e) (ex-data e)))))
      ```"

     ([exp]
      `(cb->c ~exp true))

     ([[f & forms :as _exp] auto-close?]
      (let [put-resolution!
            (gensym 'put-resolution)

            put-rejection!
            (gensym 'put-rejection)

            marks-given?
            (->> forms
                 (tree-seq coll? seq)
                 (filter symbol?)
                 (some #{'callback 'resolve 'reject}))

            forms'
            (if marks-given?
              (->> forms
                   (postwalk (partial replace-symbol 'callback put-resolution!))
                   (postwalk (partial replace-symbol 'resolve put-resolution!))
                   (postwalk (partial replace-symbol 'reject put-rejection!)))
              (-> forms (vec) (conj put-resolution!)))]

        (if (:ns &env)
          `(let [c#
                 (cljs.core.async/chan)

                 auto-close!#
                 (if ~auto-close?
                   (partial cljs.core.async/close! c#)
                   (constantly nil))

                 put-n-close!#
                 (fn [x#]
                   (when (some? x#)
                     (cljs.core.async/put! c# x#))
                   (auto-close!#))

                 ~put-resolution!
                 put-n-close!#

                 ~put-rejection!
                 (fn [x#]
                   (cond->> x#
                     (not (jtk-dvlp.async/exception? x#))
                     (ex-info "callback error" {:code :callback-error})

                     :always
                     (put-n-close!#)))]

             (try
               (~f ~@forms')
               (catch cljs.core/ExceptionInfo e#
                 (cljs.core.async/put! c# e#)
                 (cljs.core.async/close! c#))
               (catch :default e#
                 (cljs.core.async/put!
                  c# (ex-info "callback based function error" {:code :callback-based-function-error} e#))
                 (cljs.core.async/close! c#)))
             c#)

          `(let [c#
                 (clojure.core.async/chan)

                 auto-close!#
                 (if ~auto-close?
                   (partial clojure.core.async/close! c#)
                   (constantly nil))

                 put-n-close!#
                 (fn [x#]
                   (when (some? x#)
                     (clojure.core.async/put! c# x#))
                   (auto-close!#))

                 ~put-resolution!
                 put-n-close!#

                 ~put-rejection!
                 (fn [x#]
                   (cond->> x#
                     (not (jtk-dvlp.async/exception? x#))
                     (ex-info "callback error" {:code :callback-error})

                     :always
                     (put-n-close!#)))]

             (try
               (~f ~@forms')
               (catch clojure.lang.ExceptionInfo e#
                 (clojure.core.async/put! c# e#)
                 (clojure.core.async/close! c#))
               (catch Throwable e#
                 (clojure.core.async/put!
                  c# (ex-info "callback based function error" {:code :callback-based-function-error} e#))
                 (clojure.core.async/close! c#)))
             c#))))))

#?(:clj
   (defmacro <cb!
     "Like `jtk-dvlp.async/<!`, but for a callback-based call: takes
      the value the callback was given, or throws if it was rejected.
      Shorthand for `(<! (cb->c ?exp))`.

      Takes the same marks as `cb->c` — see there."
     [?exp]
     `(jtk-dvlp.async/<!
       (jtk-dvlp.async.interop.callback/cb->c ~?exp))))
