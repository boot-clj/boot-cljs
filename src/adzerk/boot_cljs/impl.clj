(ns adzerk.boot-cljs.impl
  (:require [boot.file :as file]
            [boot.kahnsort :as kahn]
            [cljs.analyzer :as ana]
            [cljs.build.api :as build]
            [cljs.env :as env]
            [clojure.java.io :as io]))

(def ^:private stored-env (atom nil))

(defn cljs-env [opts]
  (compare-and-set! stored-env nil (env/default-compiler-env opts))
  @stored-env)

(defn dep-order
  "Returns a seq of paths for all js files created by CLJS compiler, relative
  to the :output-to compiled JS file, and in dependency order."
  [env {:keys [output-dir]}]
  ; FIXME: Uses cljs compiler private data
  (let [cljs-nses (:cljs.compiler/compiled-cljs env)
        js-nses   (reduce-kv (fn [xs k v]
                               (assoc xs (str output-dir "/" (:file v)) v))
                             {}
                             (:js-dependency-index env))
        all-nses  (reduce-kv (fn [xs k v]
                               (reduce #(assoc %1 (str %2) (str k)) xs (:provides v)))
                             {}
                             (merge js-nses cljs-nses))]
    (->> cljs-nses
         (reduce-kv (fn [xs k v]
                      (assoc xs k (set (keep (comp all-nses str) (:requires v)))))
                    {})
         kahn/topo-sort
         reverse
         (map #(.getPath (file/relative-to (.getParentFile (io/file output-dir)) (io/file %)))))))

(defn compile-cljs
  "Given a seq of directories containing CLJS source files and compiler options
  opts, compiles the CLJS to produce JS files.

  Note: The files in src-paths are only the entry point for the compiler. Any
  namespaces :require'd in those files will be retrieved from the class path,
  so only application entry point namespaces need to be in src-paths."
  [input-path opts]
  (let [counter (atom 0)
        handler (conj ana/*cljs-warning-handlers*
                      (fn [warning-type env & [extra]]
                        (when (warning-type ana/*cljs-warnings*)
                          (swap! counter inc))))]
    (ana/with-warning-handlers handler
      (binding [env/*compiler* (cljs-env opts)]
        (build/build (build/inputs input-path) opts)
        (reset! stored-env env/*compiler*)
        {:warnings  @counter
         :dep-order (dep-order @env/*compiler* opts)}))))
