(ns adzerk.boot-cljs.js-deps
  (:require
    [boot.core :as core]))

(defn- max* [& args] (apply max (or (seq args) [0])))

(defn- sort-inc-js
  [x y]
  (let [[dx dy] (map :dependency-order [x y])
        [nx ny] (map core/tmppath [x y])]
    (cond (and dx dy) (compare dx dy)
          (or dx dy)  (- (compare dx dy))
          :else       (compare nx ny))))

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

(defn external
  "Given a fileset containing files with :dependency-order metadata already
  applied and a seq of tempfiles in dependency order, craetes a dependency
  order metadata map of (path => {:dependency-order n}) with entries for
  the existing files that have :dependency-order metadata plus the given
  tmpfiles such that the tmpfiles depend on the fileset."
  [fileset tmpfiles]
  (let [start (->> fileset core/ls start-dep-order)]
    (->> tmpfiles
         (map-indexed #(do [(core/tmppath %2) {:dependency-order (+ start %1)}]))
         (into {}))))

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
    {:cljs (->> srcs (core/by-ext     [".cljs"]) (sort-by :path))
     :exts (->> srcs (core/by-ext   [".ext.js"]) (sort-by :path))
     :libs (->> srcs (core/by-ext   [".lib.js"]) (sort-by :path))
     :main (->> srcs (core/by-ext [".cljs.edn"]) (sort-by :path))
     :incs (->> srcs (core/by-ext   [".inc.js"]) (sort sort-inc-js))}))
