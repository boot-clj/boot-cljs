(ns adzerk.boot-cljs.impl
  (:require [boot.file :as file]
            [boot.kahnsort :as kahn]
            [boot.pod :as pod]
            [boot.util :as butil]
            [cljs.analyzer :as ana]
            [cljs.analyzer.api :as ana-api :refer [empty-state warning-enabled?]]
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

(defn handle-ex
  "Rethrows an interesting exception if possible or the original exception.

   Exception is interesting when it has either type or tag ex-data set by cljs compiler.

   If ex-data has file property, it is changed to contain path in original source-paths.
   Exceptino message is rewriten to contain fixed path."
  [e source-paths dirs]
  (let [{:keys [type tag file line column] :as data} (util/merge-cause-ex-data e)
        ; Get the message without location info
        message (.getMessage (util/last-cause e))
        path (util/find-original-path source-paths dirs file)
        ; Add location info
        message (cond
                  column (format "ERROR: %s at file %s, line %d, column %d\n" message path line column)
                  line (format "ERROR: %s at file %s, line %d\n" message path line)
                  file (format "ERROR: %s at file %s\n" message path)
                  :else message)]

    (util/serialize-exception
      (ex-info
        message
        (-> data
            (assoc :from :boot-cljs)
            (cond-> path (assoc :file path)))
        (.getCause e)))))

(defn compile-cljs
  "Given a seq of directories containing CLJS source files and compiler options
  opts, compiles the CLJS to produce JS files."
  [input-path {:keys [optimizations] :as opts}]
  ;; So directories need to be passed to cljs compiler when compiling in dev
  ;; or there are stale namespace problems with tests. However, if compiling
  ;; with optimizations other than :none adding directories will break the
  ;; build and defeat tree shaking and :main option.
  (let [dirs (:directories pod/env)
        ; Includes also some tmp-dirs passed to this pod, but shouldn't matter
        source-paths (concat (:source-paths pod/env) (:resource-paths pod/env))
        warnings (atom [])
        handler (fn [warning-type env extra]
                  (when (warning-enabled? warning-type)
                    (when-let [s (ana/error-message warning-type extra)]
                      (let [path (util/find-original-path source-paths dirs ana/*cljs-file*)]
                        (butil/warn "WARNING: %s %s\n" s (when (:line env)
                                                           (str "at line " (:line env) " " path)))
                        (swap! warnings conj {:message s
                                              :file path
                                              :line (:line env)
                                              :type warning-type})))))]
    (try
      (build
        (apply inputs input-path dirs)
        (assoc opts :warning-handlers [handler])
        stored-env)
      {:warnings  @warnings
       :dep-order (dep-order stored-env opts)}
      (catch Exception e
        {:exception (handle-ex e source-paths dirs)}))))

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
