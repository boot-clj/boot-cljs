(ns adzerk.boot-cljs.impl
  (:require
    [clojure.pprint  :refer [pprint]]
    [clojure.java.io :as io]
    [boot.pod        :as pod]
    [boot.kahnsort   :as kahn]
    [boot.file       :as file]
    [cljs.env        :as env]
    [cljs.closure    :as cljs]
    [cljs.analyzer   :as ana])
  (:import
    [cljs.closure JavaScriptFile]
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

(defn dep-order
  [env {:keys [output-dir]}]
  (let [cljs-nses (:cljs.compiler/compiled-cljs env)
        js-nses   (-> (fn [xs k v]
                        (assoc xs (str output-dir "/" (:file v)) v))
                      (reduce-kv {} (:js-dependency-index env)))
        all-nses  (-> (fn [xs k v]
                        (reduce #(assoc %1 (str %2) (str k)) xs (:provides v)))
                      (reduce-kv {} (merge js-nses cljs-nses)))]
    (-> (fn [xs k v]
          (assoc xs k (set (keep (comp all-nses str) (:requires v)))))
        (reduce-kv {} cljs-nses)
        kahn/topo-sort
        reverse)))

(defn compile-cljs
  [src-paths {:keys [output-to] :as opts}]
  (let [counter (atom 0)
        handler (->> (fn [warning-type env & [extra]]
                       (when (warning-type ana/*cljs-warnings*)
                         (swap! counter inc)))
                     (conj ana/*cljs-warning-handlers*))]
    (ana/with-warning-handlers handler
      (binding [env/*compiler* (cljs-env opts)]
        (cljs/build (CljsSourcePaths. (filter #(.exists (io/file %)) src-paths)) opts)
        {:warnings  @counter
         :dep-order (dep-order @env/*compiler* opts)}))))
