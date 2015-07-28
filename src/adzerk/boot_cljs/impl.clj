(ns adzerk.boot-cljs.impl
  (:require [boot.file :as file]
            [boot.kahnsort :as kahn]
            [cljs.analyzer.api :refer [empty-env default-warning-handler warning-enabled?]]
            [cljs.build.api :refer [build inputs]]
            [clojure.java.io :as io]))

; Because this ns is loaded in pod, it's private to one cljs task.
; Compiler env is a atom.
(def ^:private stored-env (empty-env))

(defn dep-order
  "Returns a seq of paths for all js files created by CLJS compiler, relative
  to the :output-to compiled JS file, and in dependency order."
  [env {:keys [output-dir]}]
  ; FIXME: Uses cljs compiler private data
  (let [cljs-nses (:cljs.compiler/compiled-cljs @env)
        js-nses   (reduce-kv (fn [xs k v]
                               (assoc xs (str output-dir "/" (:file v)) v))
                             {}
                             (:js-dependency-index @env))
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
        handler (fn [warning-type env extra]
                  (when (warning-enabled? warning-type)
                    (swap! counter inc)))]
    (build
      (inputs input-path)
      (assoc opts :warning-handlers [default-warning-handler handler])
      stored-env)
    {:warnings  @counter
     :dep-order (dep-order stored-env opts)}))
