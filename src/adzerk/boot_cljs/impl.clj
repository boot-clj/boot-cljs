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
  "Returns a seq of paths for all js files created by CLJS compiler, relative
  to the :output-to compiled JS file, and in dependency order."
  [env {:keys [output-dir]}]
  (let [cljs-nses (:cljs.compiler/compiled-cljs env)
        js-nses   (-> (fn [xs k v]
                        (assoc xs (str output-dir "/" (:file v)) v))
                      (reduce-kv {} (:js-dependency-index env)))
        all-nses  (-> (fn [xs k v]
                        (reduce #(assoc %1 (str %2) (str k)) xs (:provides v)))
                      (reduce-kv {} (merge js-nses cljs-nses)))
        fs-path   #(.getPath (file/relative-to (.getParentFile (io/file output-dir)) (io/file %)))]
    (map fs-path (-> (fn [xs k v]
                       (assoc xs k (set (keep (comp all-nses str) (:requires v)))))
                     (reduce-kv {} cljs-nses)
                     kahn/topo-sort
                     reverse))))

(defn compile-cljs
  "Given a seq of directories containing CLJS source files and compiler options
  opts, compiles the CLJS to produce JS files.

  Note: The files in src-paths are only the entry point for the compiler. Any
  namespaces :require'd in those files will be retrieved from the class path,
  so only application entry point namespaces need to be in src-paths."
  [src-paths opts]
  (let [counter (atom 0)
        handler (->> (fn [warning-type env & [extra]]
                       (when (warning-type ana/*cljs-warnings*)
                         (swap! counter inc)))
                     (conj ana/*cljs-warning-handlers*))]
    (ana/with-warning-handlers handler
      (binding [env/*compiler* (cljs-env opts)]
        (cljs/build (CljsSourcePaths. (filter #(.exists (io/file %)) src-paths)) opts)
        (reset! stored-env env/*compiler*)
        {:warnings  @counter
         :dep-order (dep-order @env/*compiler* opts)}))))
