(ns adzerk.boot-cljs.middleware
  (:require [adzerk.boot-cljs.util :as util]
            [boot.file :as file]
            [boot.from.backtick :as bt]
            [clojure.java.io :as io]
            [clojure.string :as string]))

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

(defn compiler-options
  [{:keys [opts main] :as ctx}
   {:keys [compiler-options] :as task-options}]
  (assoc ctx :opts (merge (select-keys task-options [:optimizations :source-map])
                          compiler-options
                          (:compiler-options main))))

(defn main
  "Middleware to create the CLJS namespace for the build's .cljs.edn file and
  set the compiler :output-to option accordingly. The :output-to will be derived
  from the path of the .cljs.edn file (e.g. foo/bar.cljs.edn will produce the
  foo.bar CLJS namespace with output to foo/bar.js)."
  [{:keys [docroot tmp-src tmp-out main] :as ctx}]
  (let [id          (:id main)
        out-file    (io/file tmp-out docroot "out")
        out-path    (.getPath out-file)
        js-path     (util/path tmp-out docroot (str id ".js"))
        cljs-path   (util/path "boot" "cljs" (str id ".cljs"))
        cljs-file   (io/file tmp-src cljs-path)
        cljs-ns     (symbol (util/path->ns cljs-path))
        init-fns    (:init-fns main)
        requires    (into (sorted-set) (:require main))
        init-nss    (into requires (map (comp symbol namespace) init-fns))]
    (.mkdirs out-file)
    (doto cljs-file
      (io/make-parents)
      (spit (format-ns-forms (main-ns-forms cljs-ns init-nss init-fns))))
    (-> ctx
        (assoc-in [:opts :output-dir] out-path)
        (assoc-in [:opts :output-to] js-path)
        (assoc-in [:opts :main] cljs-ns))))

(defn asset-path
  "Sets :asset-path compiler option if it isn't already set."
  [{:keys [opts docroot tmp-out] :as ctx}]
  (if (:asset-path opts)
    ctx
    (let [out-path    (util/path tmp-out docroot "out")
          output-dir  (file/relative-to tmp-out (io/file out-path))
          asset-path  (util/path output-dir)]
      (assoc-in ctx [:opts :asset-path] asset-path))))

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
