(ns adzerk.boot-cljs.middleware
  (:require
    [clojure.java.io          :as io]
    [boot.from.backtick       :as bt]
    [clojure.string           :as string]
    [boot.core                :as core]
    [boot.file                :as file]
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
  [{:keys [docroot tmp-src tmp-out main] :as ctx}]
  (let [[path file] ((juxt core/tmppath core/tmpfile) main)
        base-name   (-> file .getName deps/strip-extension)
        js-path     (.getPath (io/file tmp-out (str base-name ".js")))
        cljs-path   (.getPath (io/file "boot" "cljs" (str base-name ".cljs")))
        output-dir  (file/relative-to tmp-out (io/file (get-in ctx [:opts :output-dir])))
        ; Path used by dev shim to load the files
        ; This should be relative path to output-dir
        asset-path  (.getPath (io/file docroot output-dir))
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
        (assoc-in [:opts :asset-path] asset-path)
        (update-in [:opts] (partial merge-with util/into-or-latest) (:compiler-options main-edn)))))

(defn source-map
  "Middleware to configure source map related CLJS compiler options."
  [{:keys [opts] :as ctx}]
  (if-not (:source-map opts)
    ctx
    (let [sm  (-> opts :output-to (str ".map"))
          dir (.getName (io/file (:output-dir opts)))]
      ; Under :none optimizations only true and false are valid values:
      ; https://github.com/clojure/clojurescript/wiki/Compiler-Options#source-map
      (update-in ctx [:opts] assoc
                 :source-map (if (= (:optimizations opts) :none) true sm)
                 :source-map-path dir))))
