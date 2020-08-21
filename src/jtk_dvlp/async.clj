(ns jtk-dvlp.async
  (:require
   [clojure.walk :refer [postwalk]]
   [clojure.core.async]))


(defmacro ^:private safe
  [& body]
  (if (:ns &env)
    `(try
       ~@body
       (catch cljs.core/ExceptionInfo e#
         e#)
       (catch js/Error e#
         (js-error->ex-info e# :error :unknown)))
    `(try
       ~@body
       (catch clojure.lang.ExceptionInfo e#
         e#)
       (catch Throwable e#
         (ex-info "error" {:error :unknown} e#)))))

(defmacro <e!
  "Like `<!` but tests taken val instance of `ExceptionInfo`, if so throws it.

   Usage in combination with `try-go`."
  [?exp]
  (if (:ns &env)
    `(let [v# (cljs.core.async/<! ~?exp)]
       (if (instance? cljs.core/ExceptionInfo v#)
         (throw v#)
         v#))
    `(let [v# (clojure.core.async/<! ~?exp)]
       (if (instance? clojure.lang.ExceptionInfo v#)
         (throw v#)
         v#))))

(defmacro try-go
  "Like `go` but carries thrown `ExceptionInfo` as result.

   Usage in combination with `<e!`, `<p!` and `<cb!`."
  [& body]
  (if (:ns &env)
    `(cljs.core.async/go
       (safe ~@body))
    `(clojure.core.async/go
       (safe ~@body))))

(defn ?map
  "Like `map but carries thrown `ExceptionInfo` as result.

   Usage in combination with `<e!`."
  [f chs]
  (clojure.core.async/map
   (fn [& args]
     (safe (apply f args)))
   chs))

(defmacro cb->c
  "Converts callback driven function into channel. Use `callback` symbol within
   expression `exp` forms (any level) to mark callback position in argumentlist. If no
   callback symbol is found assumes callback needs to be appended to argumentlist."
  [[fun & forms]]
  (let [put-callback-result!
        (gensym 'put-callback-result!)

        callback->put!
        (fn [form]
          (if (#{'callback} form)
            put-callback-result!
            form))

        callback-position-given?
        (->> forms
             (flatten)
             (some #{'callback}))

        forms'
        (if callback-position-given?
          (postwalk callback->put! forms)
          (-> forms (vec) (conj put-callback-result!)))]

    (if (:ns &env)
      `(try-go
        (let [callback-result#
              (cljs.core.async/promise-chan)

              ~put-callback-result!
              (fn [& args#]
                (cljs.core.async/put! callback-result# args#))

              funcall-result#
              (~fun ~@forms')]

          {:funcall-result
           funcall-result#

           :callback-result
           (cljs.core.asyn/<! callback-result#)}))
      `(try-go
        (let [callback-result#
              (clojure.core.async/promise-chan)

              ~put-callback-result!
              (fn [& args#]
                (clojure.core.async/put! callback-result# args#))

              funcall-result#
              (~fun ~@forms')]

          {:funcall-result
           funcall-result#

           :callback-result
           (clojure.core.async/<! callback-result#)})))))

(defmacro <cb!
  "Like `<!` but converts callback based expression / function `?exp` into channel
   before via `cb->c`.

   Given option `error-selector` can be used to extract an error out of exp result.
   Extracted error will be thrown as `ExceptionInfo`. No error extraction by default.

   Given option `value-selector` can be used to extract the value out of exp result
   if no error has been extracted before. Extracts first callback arg by default."
  ([?exp]
   (if (:ns &env)
     `(-> ~?exp
          (cb->c)
          (cljs.core.async/<!))
     `(-> ~?exp
          (cb->c)
          (clojure.core.async/<!))))

  ([?exp options]
   (if (:ns &env)
     `(let [value-selector'#
            (get ~options :value-selector (comp first :callback-result))

            error-selector'#
            (get ~options :error-selector (constantly nil))

            result#
            (<cb! ~?exp)]

        (if (instance? cljs.core/ExceptionInfo result#)
          (throw result#)
          (if-let [error# (error-selector'# result#)]
            (throw (js-error->ex-info error# :error :callback-based-function-error))
            (value-selector'# result#))))
     `(let [value-selector'#
            (get ~options :value-selector (comp first :callback-result))

            error-selector'#
            (get ~options :error-selector (constantly nil))

            result#
            (<cb! ~?exp)]

        (if (instance? clojure.lang.ExceptionInfo result#)
          (throw result#)
          (if-let [error# (error-selector'# result#)]
            (throw (ex-info "error" {:error :callback-based-function-error} error#))
            (value-selector'# result#)))))))
