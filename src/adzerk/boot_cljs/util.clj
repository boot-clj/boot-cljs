(ns adzerk.boot-cljs.util
  (:require
    [clojure.java.io       :as io]
    [boot.file             :as file]))

(defn into-or-latest
  [x y]
  (if (and (coll? x) (coll? y)) (into x y) y))

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

(defn delete-plain-files!
  "Delete all regular files in the given dir. (Not recursive.)"
  [dir]
  (doseq [f (-> dir .listFiles seq)]
    (when (.isFile f) (io/delete-file f true))))

(defn get-name
  [path-or-file]
  (-> path-or-file io/file .getName))

(defn rooted-file
  "Given a (possibly nil or empty) docroot and some path-segments, constructs a
  file with relative path rooted at the docroot if not nil/empty or at the first
  path segment otherwise."
  [docroot & path-segments]
  (apply io/file (keep identity (conj path-segments docroot))))

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
