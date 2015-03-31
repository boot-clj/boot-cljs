(ns adzerk.boot-cljs
  {:boot/export-tasks true}
  (:refer-clojure :exclude [compile])
  (:require
    [clojure.java.io             :as io]
    [clojure.pprint              :as pp]
    [boot.pod                    :as pod]
    [boot.core                   :as core]
    [boot.file                   :as file]
    [boot.util                   :refer [info dbug warn]]
    [adzerk.boot-cljs.util       :as util]
    [adzerk.boot-cljs.middleware :as wrap]
    [adzerk.boot-cljs.js-deps    :as deps]))

(def cljs-version "0.0-2814")

(def ^:private deps
  "ClojureScript dependency to load in the pod if
   none is provided via project"
  (delay (remove pod/dependency-loaded? `[[org.clojure/clojurescript ~cljs-version]])))

(defn warn-on-cljs-version-differences []
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

(defn- set-output-dir-opts
  [{:keys [output-dir] :as opts} tmp-out]
  (->> (doto (io/file tmp-out output-dir) .mkdirs) .getPath (assoc opts :output-dir)))

(defn- initial-context
  "Create initial context object. This is the base configuration that each
  individual compile starts with, constructed using the options provided to
  the task constructor."
  [tmp-out tmp-src tmp-result task-options]
  {:tmp-out    tmp-out
   :tmp-src    tmp-src
   :tmp-result tmp-result
   :main       nil
   :files      nil
   :opts       (-> {:libs          []
                    :externs       []
                    :preamble      []
                    :output-dir    "out"
                    :optimizations :none}
                   (into (:compiler-options task-options))
                   (merge (dissoc task-options :compiler-options))
                   (set-output-dir-opts tmp-out))})

(defn- prep-context
  "Add per-compile fields to the base context object."
  [main files docroot ctx]
  (assoc ctx :main main :files files :docroot docroot))

(defn- prep-compile
  "Given a per-compile base context, applies middleware to obtain the final,
  compiler-ready context for this build."
  [{:keys [tmp-src tmp-out main files opts] :as ctx}]
  (when (not= (:optimizations opts) :none)
    (info "Emptying compiler cache...\n")
    (doseq [dir   [tmp-src tmp-out]
            f     (.listFiles dir)
            :let  [out (io/file dir (.getName (io/file (:output-dir opts))))]
            :when (not= f out)]
      ;; Selective deletion prevents independent builds from
      ;; interfering with eachother while also preserving source maps.
      (file/delete-all f)))
  (->> ctx wrap/main wrap/level wrap/shim wrap/externs wrap/source-map))

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

(defn- copy-to-docroot
  "Copies everything the application needs, relative to its js file."
  [docroot {:keys [tmp-out tmp-result] {:keys [incs]} :files}]
  (util/copy-docroot! tmp-result docroot tmp-out)
  (doseq [[p f] (map (juxt core/tmppath core/tmpfile) incs)]
    (file/copy-with-lastmod f (io/file tmp-result (util/rooted-file docroot p)))))

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

  (let [pod-env    (-> (core/get-env) (update-in [:dependencies] into (vec (seq @deps))))
        pod        (future (pod/make-pod pod-env))
        tmp-src    (core/temp-dir!)
        tmp-out    (core/temp-dir!)
        tmp-result (core/temp-dir!)
        ctx        (initial-context tmp-out tmp-src tmp-result *opts*)]

    (warn-on-cljs-version-differences)
    (comp
      (default-main :context ctx)
      (core/with-pre-wrap fileset
        (core/empty-dir! tmp-result)
        (let [{:keys [main incs cljs] :as fs} (deps/scan-fileset fileset)]
          (loop [[m & more] main, dep-order nil]
            (let [{{:keys [optimizations]} :opts :as ctx} ctx]
              (if m
                (let [docroot   (.getParent (io/file (core/tmppath m)))
                      ctx       (prep-compile (prep-context m fs docroot ctx))
                      dep-order (->> (compile ctx @pod)
                                     (map #(.getPath (util/rooted-file docroot %)))
                                     (concat dep-order))]
                  (copy-to-docroot docroot ctx)
                  (recur more dep-order))
                (-> fileset
                    (core/add-resource tmp-result)
                    (core/add-meta (-> fileset (deps/external incs) (deps/compiled dep-order)))
                    core/commit!)))))))))
