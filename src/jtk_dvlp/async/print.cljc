(ns jtk-dvlp.async.print
  "Prints what a channel ends up with — the value, or the error it
   carries.

   For looking at a channel at the REPL. Printing a `go` channel
   directly shows the channel object, and a plain `println` on its
   value would show a carried error as an inert map rather than as the
   failure it is. These take the value out with `jtk-dvlp.async/<!`, so
   an error shows up as an error.

   Debug aids, not logging: they print, they do not report."

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
     "Takes the value of channel `<form` and hands it to `print-fn` —
      or hands it the error, if the channel carries one.

      Yields a channel with whatever `print-fn` returned, so it can be
      waited on. `<println` and `<pprint` are this with a fixed
      `print-fn`.

      WATCHOUT: The `catch :default` below is not valid Clojure on its
      own — it only compiles because `core.async` rewrites a `try` that
      contains a parking take into its own state machine, and that
      accepts `:default` on both platforms. The `<!` in the body is
      what makes it a parking take. Remove it, or move it out of the
      `try`, and this stops compiling in Clojure."
     [print-fn <form]
     `(jtk-dvlp.async/go
        (try
          (~print-fn (jtk-dvlp.async/<! ~<form))
          (catch :default e#
            (~print-fn e#))))))

#?(:clj
   (defmacro <println
     "Prints the value of channel `<form` with `println`, or the error
      it carries. Yields a channel that closes when done."
     [<form]
     `(<debug println ~<form)))

#?(:clj
   (defmacro <pprint
     "Pretty-prints the value of channel `<form`, or the error it
      carries. Yields a channel that closes when done.

      For a carried error this is the readable way to see it: the
      printed form shows message, `ex-data` and the stitched stack
      trace across the `ASYNC_BOUNDARY` marker."
     [<form]
     (let [pprint
           (if (:ns &env)
             'cljs.pprint/pprint
             'clojure.pprint/pprint)]

       `(<debug ~pprint ~<form))))
