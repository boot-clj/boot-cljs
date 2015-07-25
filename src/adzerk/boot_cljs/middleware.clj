(ns adzerk.boot-cljs.middleware
  (:require
    [clojure.java.io          :as io]
    [boot.from.backtick       :as bt]
    [clojure.string           :as string]
    [adzerk.boot-cljs.util    :as util]
    [boot.file                :as file]))

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
  [{:keys [docroot tmp-src tmp-out cljs-edn main] :as ctx}]
  (let [id          (:id main)
        out-path    (util/path tmp-out docroot "out")
        js-path     (util/path tmp-out docroot (str id ".js"))
        cljs-path   (util/path "boot" "cljs" (str id ".cljs"))
        output-dir  (file/relative-to tmp-out (io/file out-path))
        ; Path used by dev shim to load the files
        ; This should be relative path to output-dir
        asset-path  (util/path output-dir)
        cljs-file   (doto (io/file tmp-src cljs-path) io/make-parents)
        cljs-ns     (symbol (util/path->ns cljs-path))
        init-fns    (:init-fns main)
        requires    (into (sorted-set) (:require main))
        init-nss    (into requires (map (comp symbol namespace) init-fns))]
    (.mkdirs (io/file tmp-out docroot "out"))
    (spit cljs-file
          (format-ns-forms (main-ns-forms cljs-ns init-nss init-fns)))
    (-> ctx
        (assoc-in [:opts :output-dir] out-path)
        (assoc-in [:opts :output-to] js-path)
        (assoc-in [:opts :main] cljs-ns)
        (update-in [:opts :asset-path] #(or % asset-path)))))

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
