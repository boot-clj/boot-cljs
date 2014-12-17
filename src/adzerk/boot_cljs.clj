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

(def cljsc-output-filename
  "cljsc-output-c4afbd925.js")

(defn output-path-for [unified-mode f]
  (if-not unified-mode
    f
    (if-let [parent (.getParent (io/file f))]
      (io/file parent cljsc-output-filename) 
      (io/file cljsc-output-filename))))

(defn file->goog
  [path]
  (format "goog.require('%s');"
    (-> path
      (.replaceAll "\\.cljs$" "")
      (.replaceAll "[/\\\\]" "."))))

(defn write-src [inc]
  (format "document.write(\"<script src='%s'></script>\");\n" inc))

(defn write-body [code]
  (format "document.write(\"<script>%s</script>\");\n" code))

(defn write-shim! [f incs cljs output-path output-dir]
  (spit f "// boot-cljs shim\n")
  (doseq [inc incs]
    (spit f (write-src inc) :append true))
  (spit f (write-src (.getPath (io/file output-dir "goog" "base.js"))) :append true)
  (spit f (write-src output-path) :append true)
  (spit f (write-body (apply str (map file->goog cljs))) :append true))

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
   W no-warnings         bool "Suppress compiler warnings."
   u unified-mode        bool "Unified mode"]
  (if (and unified-mode (not= optimizations :none))
    (util/warn "unified-mode on; setting optimizations to :none\n"))
  (let [optimizations (if unified-mode :none optimizations)
        output-dir  (or output-dir "out")
        output-path* (or output-to "main.js")
        output-path (output-path-for unified-mode output-path*)
        tmp-dir     (core/temp-dir!)
        js-out      (io/file tmp-dir output-path)
        shim        (io/file tmp-dir output-path*)
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
        p           (pod/pod-pool pod-env)]
    (core/with-pre-wrap fileset
      (io/make-parents js-out)
      (let [srcs   (core/input-files fileset)
            ->path (comp (memfn getPath) core/tmpfile)
            incs   (->> srcs (core/by-ext [".inc.js"]) (mapv core/tmppath) sort)
            cljs   (->> srcs (core/by-ext [".cljs"])   (mapv core/tmppath))
            exts   (->> srcs (core/by-ext [".ext.js"]) (mapv ->path))
            libs   (->> srcs (core/by-ext [".lib.js"]) (mapv ->path))]
        (util/info "Compiling %s...\n" (.getName js-out))
        (swap! core/*warnings* +
               (let [preamble (if none? [] incs)]
                 (-> (pod/with-call-in (p)
                       (adzerk.boot-cljs.impl/compile-cljs
                        ~(map (memfn getPath) (core/input-dirs fileset))
                        ~(merge-with into cljs-opts {:libs libs :externs exts :preamble preamble})))
                     (get :warnings 0))))
        (when unified-mode
          (write-shim! shim incs cljs output-path output-dir))
        (-> fileset (core/add-asset tmp-dir) core/commit!)))))
