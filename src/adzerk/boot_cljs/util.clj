(ns adzerk.boot-cljs.util
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [boot.file :as file]
            [clojure.walk :as walk])
  (:import [clojure.lang ExceptionInfo]
           [java.io File]))

(defn path->js
  "Given a path to a CLJS namespace source file, returns the corresponding
  Google Closure namespace name for goog.provide() or goog.require()."
  [path]
  (-> path
      (string/replace #"\.clj([s|c])?$" "")
      (string/replace #"[/\\]" ".")))

(defn path->ns
  "Given a path to a CLJS namespace source file, returns the corresponding
  CLJS namespace name."
  [path]
  (-> (path->js path) (string/replace #"_" "-")))

(defn get-name
  [path-or-file]
  (-> path-or-file io/file .getName))

(defn path [& parts]
  (.getPath (apply io/file parts)))

(defn find-relative-path [dirs filepath]
  (if-let [file (io/file filepath)]
    (let [parent (->> dirs
                      (map io/file)
                      (some (fn [x] (if (file/parent? x file) x))))]
      (if parent (.getPath (file/relative-to parent file))))))

(defn find-original-path [source-paths dirs filepath]
  (if-let [rel-path (find-relative-path dirs filepath)]
    (or (some (fn [source-path]
                (let [f (io/file source-path rel-path)]
                  (if (.exists f)
                    (.getPath f))))
              source-paths)
        rel-path)
    filepath))

;;
;; Exception serialization
;;

(defn safe-data [data]
  (walk/postwalk
    (fn [x]
      (cond
        (instance? File x) (.getPath x)
        :else x))
    data))

(defn serialize-exception
  "Serializes given exception keeping original message, stack-trace, cause stack
   and ex-data for ExceptionInfo.

   Certain types in ex-data are converted to strings. Currently this includes
   Files."
  [e]
  {:message (.getMessage e)
   :ex-data (safe-data (ex-data e))
   :stack-trace (mapv #(select-keys (bean %) [:className :methodName :fileName :lineNumber])
                      (.getStackTrace e))
   :cause (if-let [cause (.getCause e)]
            (serialize-exception cause))})

(defn ->StackTraceElement [{:keys [className methodName fileName lineNumber]}]
  (StackTraceElement. className methodName fileName lineNumber))

(defn deserialize-exception
  "Returns mocked Exception from serialized data.

   Only overrides getStackTrace, which is used at least by clojure.stacktrace/print-stack-trace
   and Boot to print the stacktrace. Some other stuff could call directly to
   printStackTrace."
  [{:keys [message ex-data stack-trace cause]}]
  (let [cause (if cause (deserialize-exception cause))
        stack-trace (into-array StackTraceElement (map ->StackTraceElement stack-trace))]
    (if ex-data
      (proxy [ExceptionInfo] [message ex-data cause]
        (getStackTrace []
          stack-trace))
      (proxy [Throwable] [message cause]
        (getStackTrace []
          stack-trace)))))

(defn select-cause [ex p]
  (loop [ex ex]
    (if (p ex)
      ex
      (if (.getCause ex)
        (recur (.getCause ex))))))

(defn last-cause [ex]
  (loop [ex ex]
    (if (.getCause ex)
      (recur (.getCause ex))
      ex)))
