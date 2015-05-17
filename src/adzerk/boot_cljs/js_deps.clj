(ns adzerk.boot-cljs.js-deps
  (:require
    [boot.core :as core]))

(defn- max* [& args] (apply max (or (seq args) [0])))

(defn- start-dep-order
  "Returns the next dependency order index, one greater than the maximum dep
  order index in the given metadata map (path => {:dependency-order n})."
  [maps]
  (->> maps (keep :dependency-order) (apply max*) inc))

(defn strip-extension
  "Strip the .cljs.edn extension from the given main-edn-path."
  [main-edn-path]
  (.replaceAll main-edn-path "\\.cljs\\.edn$" ""))

(defn add-extension
  "Adds the .cljs.edn extension to the given path."
  [path]
  (str path ".cljs.edn"))

(defn compiled
  "Given a dep order metadata map (path => {:dependency-order n}) and a seq of
  fileset paths in dependency order, creates a new metadata map adding entries
  for the paths such that the paths depend on the preexisting entries."
  [dep-order-meta paths]
  (let [start (->> dep-order-meta vals start-dep-order)]
    (->> paths
         (map-indexed #(do [%2 {:dependency-order (+ start %1)}]))
         (into dep-order-meta))))

(defn scan-fileset
  "Scans the fileset, extracting CLJS source files, extern files, GClosure lib
  files, .main.edn files, and external JS preamble files."
  [fileset]
  (let [srcs (core/input-files fileset)]
    {:cljs (->> srcs (core/by-ext     [".cljs" ".cljc"]) (sort-by :path))
     :main (->> srcs (core/by-ext [".cljs.edn"]) (sort-by :path))}))
