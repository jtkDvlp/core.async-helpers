(ns user
  (:require
   [figwheel.main.api :as figwheel]))

(defn fig-init
  []
  (figwheel.main.api/start "dev"))
