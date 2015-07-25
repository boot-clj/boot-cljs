(ns adzerk.boot-cljs.util
  (:require [boot.core :as core]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(defn path->js
  "Given a path to a CLJS namespace source file, returns the corresponding
  Google Closure namespace name for goog.provide() or goog.require()."
  [path]
  (-> path
      (string/replace #"\.clj[s|c]$" "")
      (string/replace #"[/\\]" ".")))

(defn path->ns
  "Given a path to a CLJS namespace source file, returns the corresponding
  CLJS namespace name."
  [path]
  (-> (path->js path) (string/replace #"_" "-")))

(defn get-name
  [path-or-file]
  (-> path-or-file io/file .getName))

(defn cljs-files
  [fileset]
  (->> fileset core/input-files (core/by-ext [".cljs" ".cljc"]) (sort-by :path)))

(defn path [& parts]
  (.getPath (apply io/file parts)))

(defn main-files [fileset id]
  (let [select (if (seq id)
                 #(core/by-name [(str id ".cljs.edn")] %)
                 #(core/by-ext [".cljs.edn"] %))]
    (->> fileset
         core/input-files
         select
         (sort-by :path))))

(defn tmp-file->docroot [tmp-file]
  (.getParent (io/file (core/tmp-path tmp-file))))
