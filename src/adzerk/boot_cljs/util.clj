(ns adzerk.boot-cljs.util
  (:require
    [clojure.java.io       :as io]
    [clojure.set           :as set]
    [adzerk.boot-cljs.util :as util]
    [boot.core             :as core]
    [boot.file             :as file]))

(defn merge-new-keys
  "Merges map m2 into m1 after removing keys in m2 that are also in m1."
  [m1 m2]
  (or (and (not m2) m1)
      (->> [m2 m1]
           (map (comp set keys))
           (apply set/difference)
           (select-keys m2)
           (merge m1))))

(defn into-or-latest
  [x y]
  (if (and (coll? x) (coll? y)) (into x y) y))

(defn replace-path
  "Given a path and a name returns a file in the same directory as path but
  with the given name."
  [path name]
  (if-let [p (.getParent (io/file path))]
    (io/file p name)
    (io/file name)))

(defn path->js
  "Given a path to a CLJS namespace source file, returns the corresponding
  Google Closure namespace name for goog.provide() or goog.require()."
  [path]
  (-> path
      (.replaceAll "\\.cljs$" "")
      (.replaceAll "[/\\\\]" ".")))

(defn path->ns
  "Given a path to a CLJS namespace source file, returns the corresponding
  CLJS namespace name."
  [path]
  (-> (path->js path) (.replaceAll "_" "-")))

(defn get-name
  [path-or-file]
  (-> path-or-file io/file .getName))

(defn rooted-file
  "Given a (possibly nil or empty) docroot and some path-segments, constructs a
  file with relative path rooted at the docroot if not nil/empty or at the first
  path segment otherwise."
  [docroot & path-segments]
  (apply io/file (keep identity (conj path-segments docroot))))

(defn rooted-relative
  "Given a (possibly nil or empty) docroot and a path, resolves the path relative
  to the docroot if not nil/empty, or returns the path otherwise."
  [docroot path]
  (if (empty? docroot)
    path
    (file/relative-to docroot path)))

(defn copy-docroot!
  "Given a dest-dir, a relative path docroot, and some src-dirs, copies the
  contents of src-dirs into dest-dir/docroot/."
  [dest-dir docroot & src-dirs]
  (doseq [d src-dirs]
    (doseq [in (->> d io/file file-seq (filter (memfn isFile)))]
      (let [p (.getPath (file/relative-to d in))]
        (let [out (io/file dest-dir (rooted-file docroot p))]
          (when-not (and (.exists out) (= (.lastModified in) (.lastModified out)))
            (file/copy-with-lastmod in (io/file dest-dir (rooted-file docroot p)))))))))
