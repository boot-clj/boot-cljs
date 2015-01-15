(ns adzerk.boot-cljs
  {:boot/export-tasks true}
  (:refer-clojure :exclude [compile])
  (:require
    [clojure.java.io             :as io]
    [clojure.pprint              :as pp]
    [boot.pod                    :as pod]
    [boot.core                   :as core]
    [boot.file                   :as file]
    [boot.util                   :refer [info dbug]]
    [adzerk.boot-cljs.util       :as util]
    [adzerk.boot-cljs.middleware :as wrap]
    [adzerk.boot-cljs.js-deps    :as deps]))

(def ^:private deps
  "ClojureScript dependency to load in the pod."
  '[[org.clojure/clojurescript "0.0-2629"]])

(defn- set-output-dir-opts
  "Given a map of cljs compiler options, sets the :output-dir field correctly
  according to compilation level and whether source maps are enabled or not.
  A temp directory is created and used if the :output-dir will not be added
  to the compilation output."
  [{:keys [source-map optimizations output-dir] :as opts} tmp-out]
  (->> (if-not (or source-map (= :none optimizations))
         (core/temp-dir!)
         (doto (io/file tmp-out output-dir) .mkdirs))
       .getPath
       (assoc opts :output-dir)))

(defn- set-compilation-level-opts
  "Sets the cljs compiler options that depend on the compilation level."
  [{:keys [optimizations] :as opts}]
  (or (and (not= :advanced optimizations) opts)
      (-> opts (util/merge-new-keys {:static-fns    true
                                     :elide-asserts true
                                     :pretty-print  false}))))

(defn- initial-context
  "Create initial context object. This is the base configuration that each
  individual compile starts with, constructed using the options provided to
  the task constructor."
  [tmp-out tmp-src task-options]
  {:tmp-out tmp-out
   :tmp-src tmp-src
   :main    nil
   :files   nil
   :opts    (-> {:libs          []
                 :externs       []
                 :preamble      []
                 :output-dir    "out"
                 :optimizations :none}
                (into (:compiler-options task-options))
                (merge (dissoc task-options :compiler-options))
                set-compilation-level-opts
                (set-output-dir-opts tmp-out))})

(defn- prep-context
  "Add per-compile fields to the base context object."
  [main files docroot ctx]
  (assoc ctx :main main :files files :docroot docroot))

(defn- prep-compile
  "Given a per-compile base context, applies middleware to obtain the final,
  compiler-ready context for this build."
  [{:keys [tmp-src tmp-out main files opts] :as ctx}]
  (util/delete-plain-files! tmp-out)
  (->> ctx wrap/main wrap/shim wrap/externs wrap/source-map))

(defn- compile
  "Given a compiler context and a pod, compiles CLJS accordingly. Returns a
  seq of all compiled JS files known to the CLJS compiler in dependency order,
  as paths relative to the :output-to compiled JS file."
  [{:keys [tmp-src tmp-out main files opts] :as ctx} pod]
  (info "Compiling %s...\n" (-> opts :output-to util/get-name))
  (dbug "CLJS options:\n%s\n" (with-out-str (pp/pprint opts)))
  (let [sources [(.getPath tmp-src)]
        {:keys [warnings dep-order]}
        (pod/with-call-in pod
          (adzerk.boot-cljs.impl/compile-cljs ~sources ~opts))]
    (swap! core/*warnings* + (or warnings 0))
    (concat dep-order [(-> opts :output-to util/get-name)])))

(core/deftask ^:private default-main
  "Private task---given a base compiler context creates a .cljs.edn file and
  adds it to the fileset if none already exist. This default .cljs.edn file
  will :require all CLJS namespaces found in the fileset."
  [c context CTX edn "The cljs compiler context."]
  (let [tmp-main (core/temp-dir!)]
    (core/with-pre-wrap fileset
      (core/empty-dir! tmp-main)
      (let [{:keys [cljs main]} (deps/scan-fileset fileset)]
        (if (seq main)
          fileset
          (let [output-to (or (get-in context [:opts :output-to]) "main.js")
                out-main  (-> output-to (.replaceAll "\\.js$" "") deps/add-extension)
                out-file  (doto (io/file tmp-main out-main) io/make-parents)]
            (info "Writing %s...\n" (.getName out-file))
            (->> cljs
                 (mapv (comp symbol util/path->ns core/tmppath))
                 (assoc {} :require)
                 (spit out-file))
            (-> fileset (core/add-source tmp-main) core/commit!)))))))

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

  [O optimizations LEVEL   kw   "The optimization level."
   s source-map            bool "Create source maps for compiled JS."
   c compiler-options OPTS edn  "Options to pass to the Clojurescript compiler."]

  (let [pod-env    (-> (core/get-env) (update-in [:dependencies] into deps))
        pod        (future (pod/make-pod pod-env))
        tmp-src    (core/temp-dir!)
        tmp-out    (core/temp-dir!)
        tmp-result (core/temp-dir!)
        ctx        (initial-context tmp-out tmp-src *opts*)]
    (comp
      (default-main :context ctx)
      (core/with-pre-wrap fileset
        (core/empty-dir! tmp-result)
        (let [{:keys [main incs cljs] :as fs} (deps/scan-fileset fileset)]
          (loop [[m & more] main, {{:keys [optimizations]} :opts :as ctx} ctx, dep-order nil]
            (if m
              (let [docroot   (.getParent (io/file (core/tmppath m)))
                    ctx       (prep-compile (prep-context m fs docroot ctx))
                    dep-order (->> (compile ctx @pod)
                                   (map #(.getPath (util/rooted-file docroot %)))
                                   (concat dep-order))]
                (util/sync-docroot! tmp-result docroot tmp-out)
                (recur more ctx dep-order))
              (-> fileset
                  (core/mv-resource (when (= :none optimizations) incs))
                  (core/add-resource tmp-result)
                  (core/add-meta (-> fileset (deps/external incs) (deps/compiled dep-order)))
                  core/commit!))))))))
