(ns adzerk.boot-cljs.impl
  (:require [boot.file :as file]
            [boot.kahnsort :as kahn]
            [boot.pod :as pod]
            [boot.util :refer [dbug fail]]
            [cljs.analyzer.api :as ana-api :refer [empty-state default-warning-handler warning-enabled?]]
            [cljs.build.api :as build-api :refer [build inputs target-file-for-cljs-ns]]
            [clojure.java.io :as io]
            [adzerk.boot-cljs.util :as util]
            [ns-tracker.core :refer [ns-tracker]]))

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

(defn dep-order [env opts]
  (->> (cljs-depdendency-graph env)
       (kahn/topo-sort)
       reverse
       (map #(.getPath (target-file-for-cljs-ns % (:output-dir opts))))))

(defn handle-ex [e dirs]
  (let [{:keys [type] :as ex} (ex-data (.getCause e))]
    (cond
      (= :reader-exception type)
      (let [{:keys [file line column]} ex
            msg  (some-> e (.getCause) (.getMessage))
            path (util/find-relative-path dirs file) ]
        (fail "ERROR: %s on file %s, line %d, column %d\n" msg path line column))

      :default (throw e))))

(defn compile-cljs
  "Given a seq of directories containing CLJS source files and compiler options
  opts, compiles the CLJS to produce JS files."
  [input-path {:keys [optimizations] :as opts}]
  (let [counter (atom 0)
        handler (fn [warning-type env extra]
                  (when (warning-enabled? warning-type)
                    (swap! counter inc)))]
    (try
      (build
        input-path
        (assoc opts :warning-handlers [default-warning-handler handler])
        stored-env)
      (catch Exception e
        (handle-ex e (:directories pod/env))))
    {:warnings  @counter
     :dep-order (dep-order stored-env opts)}))

(def tracker (atom nil))

(defn reload-macros! []
  (let [dirs (:directories pod/env)]
    (when (nil? @tracker)
      (reset! tracker (ns-tracker (vec dirs))))
    ; Reload only namespaces which are already loaded
    ; As opposed to :reload-all, ns-tracker only reloads namespaces which are really changed.
    (doseq [s (filter find-ns (@tracker))]
      (dbug "Reload macro ns: %s\n" s)
      (require s :reload))))

(defn backdate-macro-dependants!
  [output-dir changed-files]
  (doseq [cljs-ns (->> changed-files
                       (map (comp symbol util/path->ns))
                       (build-api/cljs-dependents-for-macro-namespaces stored-env))]
    ; broken
    ; (build-api/mark-cljs-ns-for-recompile! cljs-ns output-dir)
    (let [f (build-api/target-file-for-cljs-ns cljs-ns output-dir)]
      (when (.exists f)
        (dbug "Backdate macro dependant cljs ns: %s\n" cljs-ns)
        (.setLastModified f 5000)))))
