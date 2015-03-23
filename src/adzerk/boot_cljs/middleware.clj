(ns adzerk.boot-cljs.middleware
  (:require
    [clojure.java.io          :as io]
    [boot.from.backtick       :as bt]
    [clojure.string           :as string]
    [boot.pod                 :as pod]
    [boot.file                :as file]
    [boot.core                :as core]
    [adzerk.boot-cljs.util    :as util]
    [adzerk.boot-cljs.js-deps :as deps]))

;; helpers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- main-ns-forms
  "Given a namespace symbol, ns-sym, a sorted set of namespace symbols this
  namespace will :require, requires, and a vector of namespace-qualified
  symbols, init-fns, constructs the forms for a namespace named ns-sym that
  :requires the namespaces in requires and calls the init-fns with no arguments
  at the top level."
  [ns-sym requires init-fns]
  [(bt/template
     (ns ~ns-sym
       (:require ~@requires)))
   (bt/template
     (do ~@(map list init-fns)))])

(defn- format-ns-forms
  "Given a sequence of forms, formats them nicely as a string."
  [forms]
  (->> forms (map pr-str) (string/join "\n\n") (format "%s\n")))

;; middleware ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn main
  "Middleware to create the CLJS namespace for the build's .cljs.edn file and
  set the compiler :output-to option accordingly. The :output-to will be derived
  from the path of the .cljs.edn file (e.g. foo/bar.cljs.edn will produce the
  foo.bar CLJS namespace with output to foo/bar.js)."
  [{:keys [tmp-src tmp-out main files opts] :as ctx}]
  (let [[path file] ((juxt core/tmppath core/tmpfile) main)
        base-name   (-> file .getName deps/strip-extension)
        ; FIXME: WINDOWS!
        parent-path (.getParent (io/file path))
        js-path     (.getPath (io/file tmp-out (str base-name ".js")))
        cljs-path   (.getPath (io/file "boot" "cljs" (str base-name ".cljs")))
        cljs-file   (doto (io/file tmp-src cljs-path) io/make-parents)
        cljs-ns     (symbol (util/path->ns cljs-path))
        main-edn    (read-string (slurp file))
        init-fns    (:init-fns main-edn)
        requires    (into (sorted-set) (:require main-edn))
        init-nss    (into requires (map (comp symbol namespace) init-fns))]
    (->> (main-ns-forms cljs-ns init-nss init-fns) format-ns-forms (spit cljs-file))
    (-> ctx
        (assoc-in [:opts :output-to] js-path)
        (assoc-in [:opts :main] cljs-ns)
        ; FIXME:
        (assoc-in [:opts :asset-path] (str parent-path "/" "out"))
        (update-in [:opts] (partial merge-with util/into-or-latest) (:compiler-options main-edn)))))

(defn shim
  [{:keys [tmp-src tmp-out main files opts docroot] :as ctx}]
  (let [incs (->> (:incs files)
                  (map core/tmppath)
                  (remove #(contains? (set (:preamble opts)) %)))]
    (update-in ctx [:opts :preamble] (comp vec distinct into) incs)))

(defn externs
  "Middleware to add externs files (i.e. files with the .ext.js extension) and
  Google Closure libs (i.e. files with the .lib.js extension) from the fileset
  to the CLJS compiler options."
  [{:keys [tmp-src tmp-out main files opts] :as ctx}]
  (let [exts (map core/tmppath (:exts files))
        libs (map core/tmppath (:libs files))]
    (update-in ctx [:opts] (partial merge-with (comp vec distinct into)) {:libs libs :externs exts})))

(defn source-map
  "Middleware to configure source map related CLJS compiler options."
  [{:keys [tmp-src tmp-out main files docroot opts] :as ctx}]
  (if-not (:source-map opts)
    ctx
    (let [sm  (-> opts :output-to (str ".map"))
          dir (.getName (io/file (:output-dir opts)))]
      (update-in ctx [:opts] assoc :source-map sm :source-map-path dir))))
