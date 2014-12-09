(ns adzerk.boot-cljs.impl
  (:require
   [clojure.java.io :as io]
   [boot.pod        :as pod]
   [boot.file       :as file]
   [cljs.env        :as env]
   [cljs.closure    :as cljs]
   [cljs.analyzer   :as ana])
  (:import
   [java.net URL URI]
   [java.util UUID]))

(defrecord CljsSourcePaths [paths]
  cljs/Compilable
  (-compile [this opts]
    (mapcat #(cljs/-compile % opts) paths)))

(def ^:private stored-env (atom nil))

(defn cljs-env [opts]
  (compare-and-set! stored-env nil (env/default-compiler-env opts))
  @stored-env)

(defn compile-cljs
  [src-paths {:keys [output-to] :as opts}]
  (let [counter (atom 0)
        handler (->> (fn [warning-type env & [extra]]
                       (when (warning-type ana/*cljs-warnings*)
                         (swap! counter inc)))
                     (conj ana/*cljs-warning-handlers*))]
    (ana/with-warning-handlers handler
      (binding [env/*compiler* (cljs-env opts)]
        (cljs/build (CljsSourcePaths. (filter #(.exists (io/file %)) src-paths)) opts)))
    {:warnings @counter}))
