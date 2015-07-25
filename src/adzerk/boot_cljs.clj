(ns adzerk.boot-cljs
  {:boot/export-tasks true}
  (:refer-clojure :exclude [compile])
  (:require
    [clojure.java.io             :as io]
    [clojure.pprint              :as pp]
    [clojure.string              :as string]
    [boot.pod                    :as pod]
    [boot.core                   :as core]
    [boot.file                   :as file]
    [boot.util                   :refer [info dbug warn]]
    [adzerk.boot-cljs.util       :as util]
    [adzerk.boot-cljs.middleware :as wrap]
    [adzerk.boot-cljs.js-deps    :as deps]))

(defn- assert-clojure-version!
  "Warn user if Clojure 1.7 is not found"
  [pod]
  (let [{:keys [major minor]} (pod/with-eval-in @pod *clojure-version*)]
    (when (or (< major 1) (and (= major 1) (< minor 7)))
      (warn "ClojureScript requires Clojure 1.7.\nSee https://github.com/boot-clj/boot/wiki/Setting-Clojure-version.\n"))))

(defn- assert-cljs! []
  (let [deps  (map :dep (pod/resolve-dependencies (core/get-env)))
        cljs? #{'org.clojure/clojurescript}]
    (if (empty? (filter (comp cljs? first) deps))
      (warn "ERROR: No ClojureScript dependency.\n"))))

(defn- initial-context
  "Create initial context object. This is the base configuration that each
   individual compile starts with, constructed using the options provided to
   the task constructor."
  [tmp-out tmp-src task-options]
  {:tmp-out    tmp-out
   :tmp-src    tmp-src
   :main       nil
   :opts       (merge (:compiler-options task-options)
                      (select-keys task-options [:optimizations :source-map]))})

(defn- prep-context
  "Add per-compile fields to the base context object."
  [ctx main docroot]
  (let [main-file (core/tmp-file main)]
    (assoc ctx
           :main (assoc (read-string (slurp main-file))
                        :path (.getPath main-file)
                        :id   (util/set-extension (.getName main-file) ".cljs.edn" ""))
           :docroot docroot)))

(defn- prep-compile
  "Given a per-compile base context, applies middleware to obtain the final,
  compiler-ready context for this build."
  [ctx]
  (-> ctx wrap/main wrap/source-map))

(defn- compile
  "Given a compiler context and a pod, compiles CLJS accordingly. Returns a
  seq of all compiled JS files known to the CLJS compiler in dependency order,
  as paths relative to the :output-to compiled JS file."
  [{:keys [tmp-src tmp-out main opts] :as ctx} pod]
  (info "Compiling %s...\n" (-> opts :output-to util/get-name))
  (dbug "CLJS options:\n%s\n" (with-out-str (pp/pprint opts)))
  (let [{:keys [warnings dep-order]}
        (pod/with-call-in pod
          (adzerk.boot-cljs.impl/compile-cljs ~(.getPath tmp-src) ~opts))]
    (swap! core/*warnings* + (or warnings 0))
    (conj dep-order (-> opts :output-to util/get-name))))

(core/deftask ^:private default-main
  "Private task---given a base compiler context creates a .cljs.edn file and
  adds it to the fileset if none already exist. This default .cljs.edn file
  will :require all CLJS namespaces found in the fileset."
  [i id      ID  str ""
   c context CTX edn "The cljs compiler context."]
  (let [tmp-main (core/tmp-dir!)]
    (core/with-pre-wrap fileset
      (core/empty-dir! tmp-main)
      (if (seq (util/main-files fileset id))
        fileset
        (let [cljs     (util/cljs-files fileset)
              out-main (str (if (seq id)
                               id
                               (-> (get-in context [:opts :output-to] "main.js")
                                   (string/replace #"\.js$" "")))
                             ".cljs.edn")
              out-file (doto (io/file tmp-main out-main) io/make-parents)]
          (info "Writing %s...\n" (.getName out-file))
          (spit out-file {:require (mapv (comp symbol util/path->ns core/tmp-path) cljs)})
          (-> fileset (core/add-source tmp-main) core/commit!))))))

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

  (let [pod        (future (pod/make-pod (core/get-env)))
        tmp-src    (core/tmp-dir!) ; For shim ns
        tmp-out    (core/tmp-dir!)
        ctx        (initial-context tmp-out tmp-src *opts*)]
    (assert-cljs!)
    (assert-clojure-version! pod)
    (comp
      (default-main :id id, :context ctx)
      (core/with-pre-wrap fileset
        (let [cljs       (util/cljs-files fileset)
              fs         (core/input-files fileset)
              main-files (util/main-files fileset id)
              cljs-edn   (first main-files)
              docroot    (util/tmp-file->docroot cljs-edn)
              ctx        (-> ctx
                             (prep-context cljs-edn docroot)
                             (prep-compile))
              dep-order  (compile ctx @pod)]
          (when (seq (rest main-files))
            (warn "WARNING: Multiple .cljs.edn files found, you should use `id` option to select one."))
          (-> fileset
              (core/add-resource tmp-out)
              (core/add-meta (-> fileset (deps/compiled dep-order)))
              core/commit!))))))
