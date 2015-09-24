(ns adzerk.boot-cljs.impl
  (:require [boot.file :as file]
            [boot.kahnsort :as kahn]
            [boot.pod :as pod]
            [boot.util :as butil]
            [cljs.analyzer :as ana]
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

(defn handle-ex [e dirs report-atom]
  (let [{:keys [type] :as ex} (ex-data (.getCause e))]
    (cond
      (= :reader-exception type)
      (let [{:keys [file line column]} ex
            msg  (some-> e (.getCause) (.getMessage))
            path (util/find-relative-path dirs file)
            exs  (format "ERROR: %s on file %s, line %d, column %d\n" msg path line column)]
        (swap! report-atom assoc :exception exs)
        (butil/fail exs))

      :default (throw e))))

(defn compile-cljs
  "Given a seq of directories containing CLJS source files and compiler options
  opts, compiles the CLJS to produce JS files."
  [input-path {:keys [optimizations] :as opts}]
  ;; So directories need to be passed to cljs compiler when compiling in dev
  ;; or there are stale namespace problems with tests. However, if compiling
  ;; with optimizations other than :none adding directories will break the
  ;; build and defeat tree shaking and :main option.
  (let [directories (when (#{nil :none} optimizations) (:directories pod/env))
        messages (atom {:exception nil
                        :warnings []})
        handler (fn [warning-type env extra]
                  (let [s (ana/error-message warning-type extra)]
                    (when (warning-enabled? warning-type)
                      (swap! messages update :warnings conj (ana/message env s)))))]
    (try
      (build
        (apply inputs input-path directories)
        (assoc opts :warning-handlers [default-warning-handler handler])
        stored-env)
      (catch Exception e
        (handle-ex e directories messages)))
    {:messages  @messages
     :dep-order (dep-order stored-env opts)}))

(def tracker (atom nil))

(defn reload-macros! []
  (let [dirs (:directories pod/env)]
    (when (nil? @tracker)
      (reset! tracker (ns-tracker (vec dirs))))
    ; Reload only namespaces which are already loaded
    ; As opposed to :reload-all, ns-tracker only reloads namespaces which are really changed.
    (doseq [s (filter find-ns (@tracker))]
      (butil/dbug "Reload macro ns: %s\n" s)
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
        (butil/dbug "Backdate macro dependant cljs ns: %s\n" cljs-ns)
        (.setLastModified f 5000)))))
