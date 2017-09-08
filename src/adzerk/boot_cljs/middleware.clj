(ns adzerk.boot-cljs.middleware
  (:require [adzerk.boot-cljs.util :as util]
            [boot.file :as file]
            [boot.util :as butil]
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
  (apply list
         (list 'ns ns-sym (apply list :require requires))
         (map list init-fns)))

(defn- format-ns-forms
  "Given a sequence of forms, formats them nicely as a string."
  [forms]
  (->> forms (map pr-str) (string/join "\n\n") (format "%s\n")))

(defn- cljs-edn-path->output-dir-path
  [cljs-edn-path]
  (str (.replaceAll cljs-edn-path "\\.cljs\\.edn$" "") ".out"))

(defn- cljs-edn-path->js-path
  [cljs-edn-path]
  (str (.replaceAll cljs-edn-path "\\.cljs\\.edn$" "") ".js"))

;; middleware ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn compiler-options
  [{:keys [opts main] :as ctx}
   {:keys [compiler-options] :as task-options}]
  (assoc ctx :opts (merge (:compiler-options main)
                          compiler-options
                          (select-keys task-options [:optimizations :source-map]))))

(defn set-option [ctx k value]
  (when-let [current-value (get-in ctx [:opts k])]
    (butil/warn "WARNING: Replacing ClojureScript compiler option %s with automatically set value.\n" k))
  (assoc-in ctx [:opts k] value))

(defn main
  "Middleware to create the CLJS namespace for the build's .cljs.edn file and
  set the compiler :output-to option accordingly. The :output-to will be derived
  from the path of the .cljs.edn file (e.g. foo/bar.cljs.edn will produce the
  foo.bar CLJS namespace with output to foo/bar.js)."
  [{:keys [tmp-src tmp-out main main-ns-name] :as ctx} write-main?]
  (let [out-rel-path (if-let [output-dir (:output-dir (:opts ctx))]
                       output-dir
                       (cljs-edn-path->output-dir-path (:rel-path main)))
        asset-path   out-rel-path
        out-file     (io/file tmp-out out-rel-path)
        out-path     (.getPath out-file)
        js-path      (if-let [output-to (:output-to (:opts ctx))]
                       (util/path tmp-out output-to)
                       (util/path tmp-out (cljs-edn-path->js-path (:rel-path main))))

        cljs-path    (util/path "boot" "cljs" (str main-ns-name ".cljs"))
        cljs-file    (io/file tmp-src cljs-path)
        cljs-ns      (symbol (util/path->ns cljs-path))
        init-fns     (:init-fns main)
        requires     (into (sorted-set) (:require main))
        ;; We set out own shim ns as :main, but if user provides :main,
        ;; include that in our shim ns requires
        requires     (if-let [main (:main (:opts ctx))]
                       (conj requires (symbol main))
                       requires)
        init-nss     (into requires (->> init-fns (keep namespace) (map symbol)))]
    (.mkdirs out-file)
    (when write-main?
      (doto cljs-file
        (io/make-parents)
        (spit (format-ns-forms (main-ns-forms cljs-ns init-nss init-fns)))))
    (-> ctx
        (update-in [:opts :asset-path] #(if % % asset-path))
        (assoc-in [:opts :output-dir] out-path)
        (assoc-in [:opts :output-to] js-path)
        (assoc-in [:opts :main] cljs-ns))))

(defn modules
  "Updates :modules :output-to paths under :compiler-options

  If module declaration has :output-to, the path is prepended with
  path to Boot-cljs temp directory.

  If :output-to is not set, default value is created based on
  the temp directory, relative path of the .cljs.edn file and module name."
  [{:keys [tmp-out main] :as ctx}]
  (when (:modules main)
    (butil/warn "WARNING: .cljs.edn :modules option is no longer supported, please set :modules under :compiler-options.\n"))
  (if (contains? (:opts ctx) :modules)
    (update-in ctx [:opts :modules]
               (fn [modules]
                 (into (empty modules)
                       (map (fn [[k v]]
                              [k (if-let [output-to (:output-to v)]
                                   (assoc v :output-to (util/path tmp-out output-to))
                                   (assoc v :output-to (util/path (-> ctx :opts :output-dir) (str (name k) ".js"))))])
                            modules))))
    ctx))

(defn source-map
  "Middleware to configure source map related CLJS compiler options."
  [{:keys [opts] :as ctx}]
  (if (contains? opts :source-map)
    (let [optimizations (:optimizations opts :none)
          ; Under :none optimizations only true and false are valid values
          ; Same if :modules is used.
          ; https://github.com/clojure/clojurescript/wiki/Compiler-Options#source-map
          sm  (if (or (= optimizations :none) (:modules opts))
                (boolean (:source-map opts))
                ;; If path is already provided, use that
                (if (string? (:source-map opts))
                  (:source-map opts)
                  ;; If non-path is provided, generate path, if the given value is truthy
                  (and (:source-map opts) (-> opts :output-to (str ".map")))))]
      (assoc-in ctx [:opts :source-map] sm))
    ctx))

(defn non-standard-defaults
  "Sets defaults differing from the ones provided by the ClojureScript compiler."
  [{:keys [opts] :as ctx}]
  (let [advanced? (= :advanced (:optimizations opts))]
    (cond-> ctx
      (and advanced? (nil? (:output-wrapper opts)))
      (assoc-in [:opts :output-wrapper] true))))
