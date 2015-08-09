(ns adzerk.boot-cljs.util
  (:require [clojure.java.io :as io]
            [clojure.string :as string]))

(defmacro assoc-when
  "Given a predicate, a collection, and key-value pairs, assocs values to keys
  in the collection for which (pred k) is true. Value expressions can safely
  involve side effects--they are not evaluated unless the predicate is true."
  [pred coll & [k v & kvs]]
  (let [pred' (gensym "pred")]
    `(let [k#     ~k
           ~pred' ~pred
           coll#  ~(if-not (seq kvs) coll `(assoc-when ~pred' ~coll ~@kvs))]
       (if-not (~pred' coll# k#) coll# (assoc coll# k# ~v)))))

(defmacro assoc-or
  "Given a collection and key-value pairs, assocs values to keys that do not
  already exist in the collection. Values are not evaluated unless they are
  added to the collection."
  [coll & kvs]
  `(assoc-when (complement contains?) ~coll ~@kvs))

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
