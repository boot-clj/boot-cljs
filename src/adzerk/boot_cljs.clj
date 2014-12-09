(ns adzerk.boot-cljs
  {:boot/export-tasks true}
  (:require
   [clojure.java.io :as io]
   [clojure.set     :as set]
   [boot.pod        :as pod]
   [boot.core       :as core]
   [boot.file       :as file]
   [boot.util       :as util]))

(def ^:private deps
  '[[org.clojure/clojurescript "0.0-2411"]])

(core/deftask cljs
  "Compile ClojureScript applications.

  The default output-path is 'main.js'.

  Available optimization levels (default 'whitespace'):

    * none         No optimizations. Bypass the Closure compiler completely.
    * whitespace   Remove comments, unnecessary whitespace, and punctuation.
    * simple       Whitespace + local variable and function parameter renaming.
    * advanced     Simple + aggressive renaming, inlining, dead code elimination, etc.

  The output-dir option is useful when using optimizations=none or when source
  maps are enabled. This option sets the name of the subdirectory (relative to
  the parent of the compiled JavaScript file) in which GClosure intermediate
  files will be written. The default name is 'out'.

  The unified option automates the process of adding the necessary <script> tags
  to HTML files when compiling with optimizations=none. When enabled, any HTML
  file that loads the output-to JS file via a script tag (as when compiling with
  optimizations) will have the base.js and goog.require() <script> tags added
  automatically."

  [d output-dir NAME     str  "Subdirectory name for GClosure intermediate files."
   n node-target         bool "Target Node.js for compilation."
   o output-to PATH      str  "The output js file path relative to docroot."
   O optimizations LEVEL kw   "The optimization level."
   p pretty-print        bool "Pretty-print compiled JS."
   s source-map          bool "Create source map for compiled JS."
   W no-warnings         bool "Suppress compiler warnings."]

  (let [output-dir  (or output-dir "out")
        output-path (or output-to "main.js")
        tmp-dir     (core/temp-dir!)
        js-out      (io/file tmp-dir output-path)
        smap        (io/file tmp-dir (str output-path ".map"))
        js-parent   (str (.getParent (io/file output-path)))
        none?       (= :none optimizations)
        keep-out?   (or source-map none?)
        out-dir     (if-not keep-out?
                      (core/temp-dir!)
                      (apply io/file tmp-dir (remove empty? [js-parent output-dir])))
        base-opts   {:libs          []
                     :externs       []
                     :preamble      []
                     :foreign-libs  []
                     :warnings      (not no-warnings)
                     :output-to     (.getPath js-out)
                     :output-dir    (.getPath out-dir)
                     :pretty-print  (boolean pretty-print)
                     :optimizations (or optimizations :whitespace)}
        ;; src-map: see https://github.com/clojure/clojurescript/wiki/Source-maps
        smap-opts   {:source-map-path (if none? js-parent output-dir)
                     :source-map      (.getPath (if none? (file/relative-to tmp-dir smap) smap))}
        cljs-opts   (merge base-opts
                           (when source-map smap-opts)
                           (when node-target {:target :nodejs}))
        pod-env     (-> (core/get-env) (update-in [:dependencies] into deps))
        p           (pod/pod-pool 2 pod-env)]
    (core/with-pre-wrap fileset
      (io/make-parents js-out)
      (let [srcs   (core/input-files fileset)
            ->path (comp (memfn getPath) core/tmpfile)
            exts   (->> srcs (core/by-ext [".ext.js"]) (map ->path))
            libs   (->> srcs (core/by-ext [".lib.js"]) (map ->path))]
        (util/info "Compiling %s...\n" (.getName js-out))
        (swap! core/*warnings* +
               (-> (pod/with-call-in (p)
                     (adzerk.boot-cljs.impl/compile-cljs
                       ~(map (memfn getPath) (core/input-dirs fileset))
                       ~(merge-with into cljs-opts {:libs libs :externs exts})))
                   (get :warnings 0)))
        (-> fileset (core/add-asset tmp-dir) core/commit!)))))
