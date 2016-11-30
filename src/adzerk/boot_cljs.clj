(ns adzerk.boot-cljs
  {:boot/export-tasks true}
  (:refer-clojure :exclude [compile])
  (:require [adzerk.boot-cljs.js-deps :as deps]
            [adzerk.boot-cljs.middleware :as wrap]
            [adzerk.boot-cljs.util :as util]
            [boot.core :as core]
            [boot.pod :as pod]
            [boot.file :as file]
            [boot.util :refer [dbug info warn guard]]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.string :as string])
  (:import [java.util.concurrent ExecutionException]))

(def cljs-version "1.7.228")

(defn- cljs-depdendency []
  (let [proj-deps    (core/get-env :dependencies)
        cljs-dep?    (first (filter (comp #{'org.clojure/clojurescript} first) proj-deps))
        cljs-exists? (io/resource "cljs/build/api.clj")]
    (cond
      ; org.clojure/clojurescript in project (non-transitive) deps - do nothing
      cljs-dep?    nil
      ; cljs.core on classpath, org.clojure/clojurescript not in project deps
      cljs-exists? (do (warn "WARNING: No ClojureScript in project dependencies but ClojureScript was found in classpath. Adding direct dependency is advised.\n") nil)
      ; no cljs on classpath, no project dep, add cljs dep to pod
      :else        ['org.clojure/clojurescript cljs-version])))

(def ^:private deps
  "ClojureScript dependency to load in the pod if
   none is provided via project"
  (delay (filter identity [(cljs-depdendency)
                           ['ns-tracker "0.3.0"]])))

(def ^:private QUALIFIERS
  "Order map for well-known Clojure version qualifiers."
  { "alpha" 0 "beta" 1 "rc" 2 "" 3})

(defn- assert-clojure-version!
  "Warn user if Clojure 1.7 or greater is not found"
  [pod]
  (let [{:keys [major minor incremental qualifier]} (pod/with-eval-in pod *clojure-version*)
        [qualifier-part1 qualifier-part2] (if-let [[_ w d] (re-find #"(\w+)(\d+)" (or qualifier ""))]
                                            [(get QUALIFIERS w) (Integer/parseInt d)]
                                            [3 0])]
    (when-not (>= (compare [major minor incremental qualifier-part1 qualifier-part2] [1 7 0 3 0]) 0)
      (warn "ClojureScript requires Clojure 1.7 or greater.\nSee https://github.com/boot-clj/boot/wiki/Setting-Clojure-version.\n"))))

(defn- read-cljs-edn
  [tmp-file]
  (let [file (core/tmp-file tmp-file)
        path (core/tmp-path tmp-file)]
    (assoc (read-string (slurp file))
           :path     (.getPath file)
           :rel-path path
           :id       (string/replace (.getName file) #"\.cljs\.edn$" ""))))

(defn- compile
  "Given a compiler context and a pod, compiles CLJS accordingly. Returns a
  seq of all compiled JS files known to the CLJS compiler in dependency order,
  as paths relative to the :output-to compiled JS file."
  [{:keys [tmp-src tmp-out main opts] :as ctx} macro-changes pod]
  (let [{:keys [output-dir]}  opts
        rel-path #(.getPath (file/relative-to tmp-out %))]
    (pod/with-call-in pod
      (adzerk.boot-cljs.impl/reload-macros!))
    (pod/with-call-in pod
      (adzerk.boot-cljs.impl/backdate-macro-dependants! ~output-dir ~macro-changes))
    (let [{:keys [warnings exception] :as result}
          (pod/with-call-in pod
            (adzerk.boot-cljs.impl/compile-cljs ~(.getPath tmp-src) ~opts))]
      (swap! core/*warnings* + (or (count warnings) 0))
      (when exception
        (throw (util/deserialize-exception exception)))
      (-> result (update-in [:dep-order] #(->> (conj % (:output-to opts)) (map rel-path)))))))

(defn- macro-files-changed
  [diff]
  (->> (core/input-files diff)
       (core/by-ext [".clj" ".cljc"])
       (map core/tmp-path)))

(defn main-files [fileset ids]
   (let [re-pat #(re-pattern (str "^\\Q" % "\\E\\.cljs\\.edn$"))
         select (if (seq ids)
                  #(core/by-re (map re-pat ids) %)
                  #(core/by-ext [".cljs.edn"] %))]
     (->> fileset
          core/input-files
          select
          (sort-by :path))))

(defn- new-pod! [tmp-src]
  (let [env (-> (core/get-env)
                (update-in [:dependencies] into @deps)
                (update-in [:directories] conj (.getPath tmp-src)))]
    (future (doto (pod/make-pod env) assert-clojure-version!))))

(defn- make-compiler
  [cljs-edn]
  (let [tmp-src (core/tmp-dir!)]
    {:pod         (new-pod! tmp-src)
     :initial-ctx {:tmp-src tmp-src
                   :tmp-out (core/tmp-dir!)
                   :main-ns-name (name (gensym "main"))}}))

(defn- compile-1
  [compilers task-opts macro-changes write-main? {:keys [path] :as cljs-edn}]
  (swap! compilers (fn [compilers]
                     (if (contains? compilers path)
                       compilers
                       (assoc compilers path (make-compiler cljs-edn)))))
  (let [{:keys [pod initial-ctx]} (get @compilers path)
        ctx (-> initial-ctx
                (assoc :main (-> (read-cljs-edn cljs-edn)
                                 (assoc :ns-name (:main-ns-name initial-ctx))))
                (wrap/compiler-options task-opts)
                (wrap/main write-main?)
                (wrap/modules)
                wrap/source-map)
        tmp (:tmp-out initial-ctx)
        out (.getPath (file/relative-to tmp (-> ctx :opts :output-to)))]
    (info "• %s\n" out)
    (dbug "CLJS options:\n%s\n" (with-out-str (pp/pprint (:opts ctx))))
    (future (compile ctx macro-changes @pod))))

(core/deftask ^:private default-main
  "Private task.

  If no .cljs.edn exists with given id, creates one. This default .cljs.edn file
  will :require all CLJS namespaces found in the fileset."
  [i ids IDS #{str} ""]
  (let [tmp-main (core/tmp-dir!)]
    (core/with-pre-wrap fileset
      (core/empty-dir! tmp-main)
      (if (seq (main-files fileset ids))
        fileset
        (let [remove-deps-cljs #(core/by-name ["deps.cljs"] % true)
              cljs     (->> fileset core/input-files (core/by-ext [".cljs" ".cljc"]) remove-deps-cljs (sort-by :path))
              out-main (str (or (first ids) "main") ".cljs.edn")
              out-file (io/file tmp-main out-main)]
          (info "Writing %s...\n" (.getName out-file))
          (doto out-file
            (io/make-parents)
            (spit {:require (mapv (comp symbol util/path->ns core/tmp-path) cljs)}))
          (-> fileset (core/add-source tmp-main) core/commit!))))))

(defn- remove-previous-cljs-output
  [fileset]
  (core/rm fileset (filter ::output (core/ls fileset))))

(defn- cljs-output-meta
  [dir]
  (-> (file-seq dir)
      (->> (filter #(.isFile %))
           (map #(.getPath (file/relative-to dir %))))
      (zipmap (repeat {::output true}))))

(defn- cljs-warnings-meta
  [cljs-edns results]
  (zipmap (map :path cljs-edns)
          (map (fn [r] {::warnings (:warnings r)}) results)))

(core/deftask cljs
  "Compile ClojureScript applications.

  Multiple builds can be compiled parallel. To define builds use .cljs.edn
  files. ID of build is the name of .cljs.edn file without the extension.
  To compile only specific builds, use ids option to select .cljs.edn files
  by name. Output files of build will be put below id.out folder in fileset.

  If no .cljs.edn files exists, default one is created. It will depend on
  all .cljs files in fileset.

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
  here: https://github.com/clojure/clojurescript/wiki/Compiler-Options.

  Cljs compiler options are merged in this order:

  1. :compiler-options in .cljs.edn file
  2. :compiler-options task option
  3. :optimizations and :source-map task options
  4. options automatically set by boot-cljs (:output-dir, :output-to, :main)"

  [i ids IDS               #{str} ""
   O optimizations LEVEL   kw   "The optimization level."
   s source-map            bool "Create source maps for compiled JS."
   c compiler-options OPTS edn  "Options to pass to the Clojurescript compiler."]

  (let [tmp-result (core/tmp-dir!)
        compilers  (atom {})
        prev       (atom nil)]
    (comp
      (default-main :ids ids)
      (core/with-pre-wrap fileset
        (info "Compiling ClojureScript...\n")
        ;; If there are any output files from other instances of the cljs
        ;; task in the pipeline we need to remove them from the classpath
        ;; or the cljs compiler will try to use them during compilation.
        (-> fileset remove-previous-cljs-output core/commit!)
        (let [diff          (core/fileset-diff @prev fileset)
              macro-changes (macro-files-changed diff)
              cljs-edns (main-files fileset ids)
              changed-cljs-edns (->> diff core/input-files (core/by-ext [".cljs.edn"]) set)
              ;; Force realization to start compilation
              futures   (doall (map (fn [cljs-edn]
                                      (let [write-main?  (contains? changed-cljs-edns cljs-edn)]
                                        (compile-1 compilers *opts* macro-changes write-main? cljs-edn)))
                                    cljs-edns))
              ;; Wait for all compilations to finish
              ;; Remove unnecessary layer of cause stack added by futures
              results   (try (doall (map deref futures))
                             (catch ExecutionException e
                               (throw (.getCause e))))
              ;; Since each build has its own :output-dir we don't need to do
              ;; anything special to merge dependency ordering of files across
              ;; builds. Each :output-to js file will depend only on compiled
              ;; namespaces in its own :output-dir, including goog/base.js etc.
              dep-order (reduce into [] (map :dep-order results))]
          (reset! prev fileset)
          (->> (vals @compilers)
               (map #(get-in % [:initial-ctx :tmp-out]))
               (apply core/sync! tmp-result))
          (-> fileset
              (core/add-resource tmp-result)
              ;; Add warnings to .cljs.edn files metadata so other tasks
              ;; can use this information and report it to users
              (core/add-meta (cljs-warnings-meta cljs-edns results))
              ;; Add metadata to mark the output of this task so subsequent
              ;; instances of the cljs task can remove them before compiling.
              (core/add-meta (cljs-output-meta tmp-result))
              (core/add-meta (deps/compiled fileset dep-order))
              core/commit!))))))

(boot/deftask cljs-edn
  "Generate a .cljs.edn file."
  [e edn      VAL str   "EDN file name."
   r require  VAL [sym] "Vector of namespaces to require."
   f init-fns VAL [sym] "Vector of fuctions to wrap in do block."
   t target   VAL kw    "Target platform."
   d closure-defines  VAL {} "A map of closure defines options."
   o compiler-options VAL {} "A map of compiler options."]
   (assert (:edn *opts*) "Must provide an edn file name.")
   (assert (:init-fns *opts*) "Must provide an init-fn.")
   (let [edn    (:edn *opts*)
         init   (:init-fn *opts*)
         main   (symbol (namespace init))
         ednstr {:require (:require *opts* [])
                 :init-fns (:init-fns *opts* [])
                 :compiler-options (:compiler-options *opts*
                                     {:target (:target *opts*)
                                      :closure-defines (:closure-defines *opts*)})}
         tmp    (boot/tmp-dir!)
         fname  (str edn ".cljs.edn")
         fedn   (io/file tmp fname)]
     (boot/with-pre-wrap fileset
       (util/info (str "Generating EDN file: " fname "\n"))
       (doto fedn (spit ednstr))
       (-> fileset (boot/add-resource tmp) boot/commit!))))
