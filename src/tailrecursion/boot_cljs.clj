(ns ^:boot/export-tasks tailrecursion.boot-cljs
  (:require
   [clojure.java.io :as io]
   [boot.pod        :as pod]
   [boot.core       :as core]
   [boot.util       :as util]))

(def cljs-deps '[[org.clojure/clojurescript "0.0-2371"]])

(core/deftask cljs
  "Compile ClojureScript applications.

  The default output-path is 'main.js'.

  Available optimization levels (default 'whitespace'):

    * none         No optimizations.
    * whitespace   Remove comments, unnecessary whitespace, and punctuation.
    * simple       Whitespace + local variable and function parameter renaming.
    * advanced     Simple + aggressive renaming, inlining, dead code elimination, etc.

  The output-dir option is useful when using optimizations 'none' or when source
  maps are enabled. This option sets the name of the subdirectory (relative to
  the output-path–the compiled JavaScript file) in which GClosure intermediate
  files will be written. The default name is 'out'.

  When using optimization level 'none':

    <body>
      ...
      <script type='text/javascript' src='out/goog/base.js'></script>
      <script type='text/javascript' src='main.js'></script>
      <script type='text/javascript'>goog.require('my.namespace')</script>
    </body>

  Any other optimization level:

    <body>
      ...
      <script type='text/javascript' src='main.js'></script>
    </body>

  (Replace 'out', 'main.js', and 'my-namespace' with your own configuration.)"

  [d output-dir NAME     str  "Subdirectory name for GClosure intermediate files."
   o output-path PATH    str  "The output js file path relative to docroot."
   O optimizations LEVEL kw   "The optimization level."
   p pretty-print        bool "Pretty-print compiled JS."
   s source-map          bool "Create source map for compiled JS."
   W no-warnings         bool "Suppress compiler warnings."]

  (let [output-path (or output-path "main.js")
        inc-dir     (core/mksrcdir!)
        lib-dir     (core/mksrcdir!)
        ext-dir     (core/mksrcdir!)
        tgt-dir     (core/mktmpdir!)
        stage-dir   (core/mktgtdir!)
        js-out      (io/file tgt-dir output-path)
        smap        (io/file tgt-dir (str output-path ".map"))
        js-parent   (str (.getParent (io/file output-path)))
        keep-out?   (or source-map (= :none optimizations))
        out-dir     (if-not keep-out?
                      (core/mktmpdir!)
                      (apply io/file tgt-dir (remove empty? [js-parent "out"])))
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
        smap-opts   {:source-map-path js-parent
                     :source-map      (.getPath smap)}
        cljs-opts   (merge base-opts (when source-map smap-opts))
        ->res       (partial map core/resource-path)
        p           (-> (core/get-env)
                      (update-in [:dependencies] into cljs-deps)
                      pod/make-pod future)
        {:keys [incs exts libs]}
        (->> (pod/call-in @p
               `(tailrecursion.boot-cljs.impl/install-dep-files
                  ~(core/get-env)
                  ~(.getPath inc-dir)
                  ~(.getPath ext-dir)
                  ~(.getPath lib-dir)))
          (reduce-kv #(assoc %1 %2 (->res %3)) {}))]
    (core/with-pre-wrap
      (io/make-parents js-out)
      (util/info "Compiling %s...\n" (.getName js-out))
      (let [srcs  (core/src-files)
            cljs  (->> srcs (core/by-ext [".cljs"]))
            exts' (->> srcs (core/by-ext [".ext.js"]))
            libs' (->> srcs (core/by-ext [".lib.js"]))
            incs' (->> srcs (core/by-ext [".inc.js"]))]
        (swap! core/*warnings* +
          (-> (pod/call-in @p
                `(tailrecursion.boot-cljs.impl/compile-cljs
                   ~(seq (core/get-env :src-paths))
                   ~(merge-with into cljs-opts {:libs     (concat libs (->res libs'))
                                                :externs  (concat exts (->res exts'))
                                                :preamble (concat incs (->res (sort incs')))})))
            (get :warnings 0)))
        (core/sync! stage-dir tgt-dir)
        (when-not keep-out?
          (doseq [f (concat cljs exts' libs' incs')] (core/consume-file! f)))))))
