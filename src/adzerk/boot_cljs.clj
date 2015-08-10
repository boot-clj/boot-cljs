(ns adzerk.boot-cljs
  {:boot/export-tasks true}
  (:refer-clojure :exclude [compile])
  (:require [adzerk.boot-cljs.js-deps :as deps]
            [adzerk.boot-cljs.middleware :as wrap]
            [adzerk.boot-cljs.util :as util]
            [boot.core :as core]
            [boot.pod :as pod]
            [boot.util :refer [dbug info warn]]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.string :as string]))

(def cljs-version "1.7.48")

(def ^:private deps
  "ClojureScript dependency to load in the pod if
   none is provided via project"
  (delay (remove pod/dependency-loaded?
                 [['org.clojure/clojurescript cljs-version]
                  ['ns-tracker "0.3.0"]])))

(def ^:private QUALIFIERS
  "Order map for well-known Clojure version qualifiers."
  { "alpha" 0 "beta" 1 "rc" 2 "" 3})

(defn- assert-clojure-version!
  "Warn user if Clojure 1.7 or greater is not found"
  [pod]
  (let [{:keys [major minor incremental qualifier]} (pod/with-eval-in @pod *clojure-version*)
        [qualifier-part1 qualifier-part2] (if-let [[_ w d] (re-find #"(\w+)(\d+)" (or qualifier ""))]
                                            [(get QUALIFIERS w) (Integer/parseInt d)]
                                            [3 0])]
    (when-not (>= (compare [major minor incremental qualifier-part1 qualifier-part2] [1 7 0 3 0]) 0)
      (warn "ClojureScript requires Clojure 1.7 or greater.\nSee https://github.com/boot-clj/boot/wiki/Setting-Clojure-version.\n"))))

(defn- assert-cljs-dependency! []
  (let [proj-deps  (core/get-env :dependencies)
        proj-dep?  (set (map first proj-deps))
        all-deps   (map :dep (pod/resolve-dependencies (core/get-env)))
        trans-deps (remove #(-> % first proj-dep?) all-deps)
        cljs?      #{'org.clojure/clojurescript}
        find-cljs  (fn [ds] (first (filter #(-> % first cljs?) ds)))
        trans-cljs (find-cljs trans-deps)
        proj-cljs  (find-cljs proj-deps)]
    (cond
      (and proj-cljs (neg? (compare (second proj-cljs) cljs-version)))
      (warn "WARNING: CLJS version older than boot-cljs: %s\n" (second proj-cljs))
      (and trans-cljs (not= (second trans-cljs) cljs-version))
      (warn "WARNING: Different CLJS version via transitive dependency: %s\n" (second trans-cljs)))))

(defn- read-cljs-edn
  [tmp-file]
  (let [file (core/tmp-file tmp-file)]
    (assoc (read-string (slurp file))
           :path (.getPath file)
           :id   (string/replace (.getName file) #"\.cljs\.edn$" ""))))

(defn- compile
  "Given a compiler context and a pod, compiles CLJS accordingly. Returns a
  seq of all compiled JS files known to the CLJS compiler in dependency order,
  as paths relative to the :output-to compiled JS file."
  [{:keys [tmp-src tmp-out main opts] :as ctx} macro-changes pod]
  (let [{:keys [output-dir]}  opts
        {:keys [directories]} (core/get-env)]
    (pod/with-call-in pod
      (adzerk.boot-cljs.impl/reload-macros! ~directories))
    (pod/with-call-in pod
      (adzerk.boot-cljs.impl/backdate-macro-dependants! ~output-dir ~macro-changes))
    (let [{:keys [warnings dep-order]}
          (pod/with-call-in pod
            (adzerk.boot-cljs.impl/compile-cljs ~(.getPath tmp-src) ~opts))]
      (swap! core/*warnings* + (or warnings 0))
      (conj dep-order (-> opts :output-to util/get-name)))))

(defn- cljs-files
  [fileset]
  (->> fileset core/input-files (core/by-ext [".cljs" ".cljc"]) (sort-by :path)))

(defn- fs-diff!
  [state fileset]
  (let [s @state]
    (reset! state fileset)
    (core/fileset-diff s fileset)))

(defn- macro-files-changed
  [diff]
  (->> (core/input-files diff)
       (core/by-ext [".clj" ".cljc"])
       (map core/tmp-path)))

(defn main-files
  ([fileset] (main-files fileset nil))
  ([fileset id]
   (let [select (if (seq id)
                  #(core/by-name [(str id ".cljs.edn")] %)
                  #(core/by-ext [".cljs.edn"] %))]
     (->> fileset
          core/input-files
          select
          (sort-by :path)))))

(core/deftask ^:private default-main
  "Private task.

  If no .cljs.edn exists with given id, creates one. This default .cljs.edn file
  will :require all CLJS namespaces found in the fileset."
  [i id ID str ""]
  (let [tmp-main (core/tmp-dir!)]
    (core/with-pre-wrap fileset
      (core/empty-dir! tmp-main)
      (if (seq (main-files fileset id))
        fileset
        (let [cljs     (cljs-files fileset)
              out-main (str (or id "main") ".cljs.edn")
              out-file (io/file tmp-main out-main)]
          (info "Writing %s...\n" (.getName out-file))
          (doto out-file
            (io/make-parents)
            (spit {:require (mapv (comp symbol util/path->ns core/tmp-path) cljs)}))
          (-> fileset (core/add-source tmp-main) core/commit!))))))

(defn tmp-file->docroot [tmp-file]
  (or (.getParent (io/file (core/tmp-path tmp-file))) ""))

(core/deftask cljs
  "Compile ClojureScript applications.

   Available --optimization levels (default 'none'):

   * none         No optimizations. Bypass the Closure compiler completely.
   * whitespace   Remove comments, unnecessary whitespace, and punctuation.
   * simple       Whitespace + local variable and function parameter renaming.
   * advanced     Simple + aggressive renaming, inlining, dead code elimination.

   Source maps can be enabled via the --source-map flag. This provides what the
   browser needs to map locations in the compiled JavaScript to the corresponding
   locations in the original ClojureScript source files.

   The --compiler-options option can be used to set any other options that should
   be passed to the Clojurescript compiler. A full list of options can be found
   here: https://github.com/clojure/clojurescript/wiki/Compiler-Options."

  [i id ID                 str  ""
   O optimizations LEVEL   kw   "The optimization level."
   s source-map            bool "Create source maps for compiled JS."
   c compiler-options OPTS edn  "Options to pass to the Clojurescript compiler."]

  (let [pod-env    (update-in (core/get-env) [:dependencies] into @deps)
        pod        (future (pod/make-pod pod-env))
        tmp-src    (core/tmp-dir!) ; For shim ns
        tmp-out    (core/tmp-dir!)
        prev       (atom nil)]
    (assert-cljs-dependency!)
    (assert-clojure-version! pod)
    (comp
      (default-main :id id)
      (core/with-pre-wrap fileset
        (info "Compiling ClojureScript...\n")
        (let [main-files (main-files fileset id)
              cljs-edn   (first main-files)
              diff       (fs-diff! prev fileset)
              macro-changes (macro-files-changed diff)
              ctx        (-> {:tmp-out tmp-out
                              :tmp-src tmp-src
                              :docroot (tmp-file->docroot cljs-edn)
                              :main    (read-cljs-edn cljs-edn)}
                             (wrap/compiler-options *opts*)
                             wrap/main
                             wrap/asset-path
                             wrap/source-map)
              dep-order (compile ctx macro-changes @pod)]
          (reset! prev fileset)
          (when (seq (rest main-files))
            (warn "WARNING: Multiple .cljs.edn files found, you should use `id` option to select one."))
          (-> fileset
              (core/add-resource tmp-out)
              (core/add-meta (-> fileset (deps/compiled dep-order)))
              core/commit!))))))
