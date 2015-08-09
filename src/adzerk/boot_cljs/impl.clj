(ns adzerk.boot-cljs.impl
  (:require [boot.file :as file]
            [boot.kahnsort :as kahn]
            [cljs.analyzer.api :as ana-api :refer [empty-state default-warning-handler warning-enabled?]]
            [cljs.build.api :refer [build inputs target-file-for-cljs-ns]]
            [clojure.java.io :as io]))

; Because this ns is loaded in pod, it's private to one cljs task.
; Compiler env is a atom.
(def ^:private stored-env (empty-state))

(defn ns-dependencies
  "Given a namespace as a symbol return list of namespaces required by the namespace."
  ; ([ns] (ns-dependencies env/*compiler* ns))
  ([state ns]
   (vals (:requires (ana-api/find-ns state ns)))))

(defn cljs-depdendency-graph [state]
  (let [all-ns (ana-api/all-ns state)
        all-ns-set (set all-ns)]
    (->> all-ns
         (reduce (fn [acc n]
                   (assoc acc n (->> (ns-dependencies state n)
                                     (keep all-ns-set)
                                     (set))))
                 {}))))

(defn dep-order [env]
  (->> (cljs-depdendency-graph env)
       (kahn/topo-sort)
       reverse
       (map #(.getPath (target-file-for-cljs-ns %)))))

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
     :dep-order (dep-order stored-env)}))
