(ns jtk-dvlp.async.interop.callback
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
     "Creates a `chan` base on the callbacks of `exp`. Symbols
      `callback` / `resolve` and `reject` marks the callback
      position of `exp` to put resolutions and rejections onto
      the new created channel. If no mark is given, assumes `resolve`
      position is the last arg of `exp` and will append it.

      `exp` callbacks must expect only one argument!

      Rejections will be used as `cause` for a new created `ExceptionInfo`.

      Given `auto-close?` `false` the caller of `cb->c` is responsible for
      closing the channel! Otherwise channel will be closed after first put
      (resolve or reject).

      If calling `exp` fails an error / exception will be put onto the
      new created channel with fail information. The channel will be
      closed then.

      Example:
      ```
      (let [callback-based-fn
            (fn callback-based-fn
              [value-to-carry has-to-fail? success fail]
              (Thread/sleep 1000)
              (if has-to-fail?
                (fail [:nope value-to-carry])
                (success [:yeah value-to-carry])))]

        (go
         (try
           (-> (callback-based-fn
                5 true
                resolve
                ;; fn must be inline so that `cb->c` can recognize `reject` mark!
                (fn modifiy-error-before-reject-it [error]
                  (->> {:error error}
                       (ex-info \"nix-gut\")
                       (reject))))

               (<cb!)
               (println))

           (catch ExceptionInfo e
         (println e)))))
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
                 (flatten)
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

                 ~put-resolution!
                 (fn [x#]
                   (cljs.core.async/put! c# x#)
                   (auto-close!#))

                 ~put-rejection!
                 (fn [x#]
                   (->> x#
                        (ex-info "callback error" {:code :callback-error})
                        (cljs.core.async/put! c#))
                   (auto-close!#))]

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

                 ~put-resolution!
                 (fn [x#]
                   (clojure.core.async/put! c# x#)
                   (auto-close!#))

                 ~put-rejection!
                 (fn [x#]
                   (->> x#
                        (ex-info "callback error" {:code :callback-error})
                        (clojure.core.async/put! c#))
                   (auto-close!#))]

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
     "Like `<!` for callback based functions via `cb->c` convertion."
     [?exp]
     `(jtk-dvlp.async/<!
       (jtk-dvlp.async.interop.callback/cb->c ~?exp))))
