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
  '[[enlive "1.1.5"]
    [org.clojure/clojurescript "0.0-2371"]])

(def ^:private last-cljs (atom {}))
(def ^:private last-html (atom #{}))

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
   u unified             bool "Add <script> tags to html when optimizations=none."
   W no-warnings         bool "Suppress compiler warnings."]

  (let [output-dir  (or output-dir "out")
        output-path (or output-to "main.js")
        inc-dir     (core/mksrcdir!)
        lib-dir     (core/mksrcdir!)
        ext-dir     (core/mksrcdir!)
        tmp-dir     (core/mktmpdir!)
        html-dir    (core/mktmpdir!)
        stage-dir   (core/mktgtdir!)
        js-out      (io/file tmp-dir output-path)
        smap        (io/file tmp-dir (str output-path ".map"))
        js-parent   (str (.getParent (io/file output-path)))
        none?       (= :none optimizations)
        keep-out?   (or source-map none?)
        out-dir     (if-not keep-out?
                      (core/mktmpdir!)
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
        ->res       (partial map core/resource-path)
        p           (-> (core/get-env)
                      (update-in [:dependencies] into deps)
                      pod/make-pod future)
        {:keys [incs exts libs]}
        (->> (pod/call-in @p
               `(adzerk.boot-cljs.impl/install-dep-files
                  ~(core/get-env)
                  ~(.getPath inc-dir)
                  ~(.getPath ext-dir)
                  ~(.getPath lib-dir)))
          (reduce-kv #(assoc %1 %2 (->res %3)) {}))]
    (core/with-pre-wrap
      (io/make-parents js-out)
      (let [srcs     (core/src-files+)
            cljs     (->> srcs (core/by-ext [".cljs"]))
            lastc    (->> cljs (reduce #(assoc %1 %2 (.lastModified %2)) {}))
            exts'    (->> srcs (core/by-ext [".ext.js"]))
            libs'    (->> srcs (core/by-ext [".lib.js"]))
            incs'    (->> srcs (core/by-ext [".inc.js"]))
            html     (->> (core/all-files) (core/by-ext [".html"]))
            exts*    (concat exts (->res exts'))
            libs*    (concat exts (->res libs'))
            incs*    (concat incs (->res (sort incs')))
            dirty-c? (not= @last-cljs (reset! last-cljs lastc))
            lasth    (->> html (reduce #(conj %1 [%2 (.lastModified %2)]) #{}))
            dirty-h  (set (map first (set/difference lasth @last-html)))
            remov-h  (set/difference (set (map first @last-html)) (set (map first lasth)))
            notify-h (delay (util/info "Adding <script> tags to html...\n"))]
        (reset! last-html lasth)
        (when dirty-c?
          (util/info "Compiling %s...\n" (.getName js-out))
          (swap! core/*warnings* +
            (-> (pod/call-in @p
                  `(adzerk.boot-cljs.impl/compile-cljs
                     ~(seq (core/get-env :src-paths))
                     ~(merge-with into
                        cljs-opts {:libs     libs*
                                   :externs  exts*
                                   :preamble incs*})))
              (get :warnings 0))))
        (when (and unified none? (seq html))
          (let [cljs (map core/relative-path cljs)]
            (doseq [f html]
              (let [content   (slurp f)
                    html-path (core/relative-path f)
                    out-file  (io/file html-dir html-path)]
                (when (or dirty-c? (contains? dirty-h f))
                  @notify-h
                  (io/make-parents out-file)
                  (spit out-file
                    (pod/call-in @p
                      `(adzerk.boot-cljs.impl/add-script-tags
                         ~content
                         ~html-path
                         ~output-path
                         ~output-dir
                         ~cljs
                         ~(->> incs* (map (comp slurp io/resource))))))
                  (.setLastModified out-file (.lastModified f)))
                (when (contains? remov-h f) (io/delete-file out-file true))
                (core/consume-file! f)))))
        (core/sync! stage-dir tmp-dir html-dir)
        (when-not keep-out?
          (doseq [f (concat cljs exts' libs' incs')] (core/consume-file! f)))))))
