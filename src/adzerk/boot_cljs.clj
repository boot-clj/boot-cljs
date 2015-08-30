(ns adzerk.boot-cljs
  {:boot/export-tasks true}
  (:refer-clojure :exclude [compile])
  (:require [adzerk.boot-cljs.js-deps :as deps]
            [adzerk.boot-cljs.middleware :as wrap]
            [adzerk.boot-cljs.util :as util]
            [boot.core :as core]
            [boot.pod :as pod]
            [boot.file :as file]
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
  (let [{:keys [major minor incremental qualifier]} (pod/with-eval-in pod *clojure-version*)
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
        parse-v    #(when % (string/split % #"\."))
        trans-cljs (find-cljs trans-deps)
        proj-cljs  (find-cljs proj-deps)]
    (cond
      (and proj-cljs (pos? (compare (parse-v (second proj-cljs)) (parse-v cljs-version))))
      (warn "WARNING: CLJS version older than boot-cljs: %s\n" (second proj-cljs))
      (and trans-cljs (not= (second trans-cljs) cljs-version))
      (warn "WARNING: Different CLJS version via transitive dependency: %s\n" (second trans-cljs)))))

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
    (let [{:keys [warnings] :as result}
          (pod/with-call-in pod
            (adzerk.boot-cljs.impl/compile-cljs ~(.getPath tmp-src) ~opts))]
      (swap! core/*warnings* + (or warnings 0))
      (-> result (update-in [:dep-order] #(->> (conj % (:output-to opts)) (map rel-path)))))))

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
  ([fileset ids]
   (let [re-pat #(re-pattern (str "^\\Q" % "\\E\\.cljs\\.edn$"))
         select (if (seq ids)
                  #(core/by-re (map re-pat ids) %)
                  #(core/by-ext [".cljs.edn"] %))]
     (->> fileset
          core/input-files
          select
          (sort-by :path)))))

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
                   :main    (-> (read-cljs-edn cljs-edn)
                                (assoc :ns-name (name (gensym "main"))))}}))

(defn- compile-1
  [compilers task-opts macro-changes {:keys [path] :as cljs-edn}]
  (swap! compilers (fn [compilers]
                     (if (contains? compilers path)
                       compilers
                       (assoc compilers path (make-compiler cljs-edn)))))
  (let [{:keys [pod initial-ctx]} (get @compilers path)
        ctx (-> initial-ctx
                (wrap/compiler-options task-opts)
                wrap/main
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
        (let [cljs     (cljs-files fileset)
              out-main (str (or (first ids) "main") ".cljs.edn")
              out-file (io/file tmp-main out-main)]
          (info "Writing %s...\n" (.getName out-file))
          (doto out-file
            (io/make-parents)
            (spit {:require (mapv (comp symbol util/path->ns core/tmp-path) cljs)}))
          (-> fileset (core/add-source tmp-main) core/commit!))))))

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
  here: https://github.com/clojure/clojurescript/wiki/Compiler-Options."

  [i ids IDS               #{str} ""
   O optimizations LEVEL   kw   "The optimization level."
   s source-map            bool "Create source maps for compiled JS."
   c compiler-options OPTS edn  "Options to pass to the Clojurescript compiler."]

  (let [tmp-result (core/tmp-dir!)
        compilers  (atom {})
        prev       (atom nil)]
    (assert-cljs-dependency!)
    (comp
      (default-main :ids ids)
      (core/with-pre-wrap fileset
        (info "Compiling ClojureScript...\n")
        ;; If there are any output files from other instances of the cljs
        ;; task in the pipeline we need to remove them from the classpath
        ;; or the cljs compiler will try to use them during compilation.
        (let [diff          (fs-diff! prev fileset)
              macro-changes (macro-files-changed diff)
              cljs-edns (main-files fileset ids)
              ;; Force realization to start compilation
              futures   (doall (map (partial compile-1 compilers *opts* macro-changes) cljs-edns))
              ;; Wait for all compilations to finish
              results   (doall (map deref futures))
              ;; Since each build has its own :output-dir we don't need to do
              ;; anything special to merge dependency ordering of files across
              ;; builds. Each :output-to js file will depend only on compiled
              ;; namespaces in its own :output-dir, including goog/base.js etc.
              dep-order (reduce into [] (map :dep-order results))]
          (->> (vals @compilers)
               (map #(get-in % [:initial-ctx :tmp-out]))
               (apply core/sync! tmp-result))
          (-> fileset
              (core/add-resource tmp-result)
              ;; Add metadata to mark the output of this task so subsequent
              ;; instances of the cljs task can remove them before compiling.
              (core/add-meta (deps/compiled fileset dep-order))
              core/commit!))))))
