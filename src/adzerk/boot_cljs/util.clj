(ns adzerk.boot-cljs.util
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [boot.file :as file]))

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
      (.getPath (if parent
                  (file/relative-to parent file)
                  file)))))
