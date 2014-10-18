(ns tailrecursion.boot-cljs
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

  none         No optimizations.
  whitespace   Remove comments, unnecessary whitespace, and punctuation.
  simple       Whitespace + local variable and function parameter renaming.
  advanced     Simple + aggressive renaming, inlining, dead code elimination, etc."

  [o output-path PATH  str  "The output js file path relative to docroot."
   O optimizations OPT kw   "The optimization level."
   p pretty-print      bool "Pretty-print compiled JS."
   s source-map        bool "Create source map for compiled JS."
   W no-warnings       bool "Suppress compiler warnings."]

  (let [p (-> (core/get-env)
            (update-in [:dependencies] into cljs-deps)
            pod/make-pod future)
        output-path (or output-path "main.js")
        inc-dir     (core/mktmpdir!)
        lib-dir     (core/mktmpdir!)
        ext-dir     (core/mktmpdir!)
        out-dir     (core/mktmpdir!)
        tgt-dir     (core/mktgtdir!)
        js-out      (io/file tgt-dir output-path)
        smap        (io/file tgt-dir (str output-path ".map"))
        smap-path   (str (.getParent (io/file output-path)))
        base-opts   {:libs          []
                     :externs       []
                     :preamble      []
                     :foreign-libs  []
                     :warnings      (not no-warnings)
                     :output-to     (.getPath js-out)
                     :output-dir    (.getPath out-dir)
                     :pretty-print  (boolean pretty-print)
                     :optimizations (or optimizations :whitespace)}
        ;; see https://github.com/clojure/clojurescript/wiki/Source-maps
        smap-opts   {:source-map-path smap-path
                     :source-map      (.getPath smap)
                     :output-dir      (.getPath (if (empty? smap-path)
                                                  tgt-dir
                                                  (io/file tgt-dir smap-path)))}
        cljs-opts   (merge base-opts (when source-map smap-opts))
        {:keys [incs exts libs]
         :as instl} (pod/call-in @p
                      `(tailrecursion.boot-cljs.impl/install-dep-files
                         ~(core/get-env)
                         ~(.getPath inc-dir)
                         ~(.getPath ext-dir)
                         ~(.getPath lib-dir)))]
    (core/with-pre-wrap
      (io/make-parents js-out)
      (util/info "Compiling ClojureScript...\n")
      (let [intov (fnil into [])
            srcs  (core/src-files)
            cljs  (->> srcs (core/by-ext [".cljs"]))
            exts' (->> srcs (core/by-ext [".ext.js"]) (map (memfn getPath)))
            libs' (->> srcs (core/by-ext [".lib.js"]) (map (memfn getPath)))
            incs' (->> srcs (core/by-ext [".inc.js"]) (map (memfn getPath)))]
        (pod/call-in @p
          `(tailrecursion.boot-cljs.impl/compile-cljs
             ~(seq (core/get-env :src-paths))
             ~(merge-with intov cljs-opts {:libs     (intov libs libs')
                                           :externs  (intov exts exts')
                                           :preamble (intov incs (sort incs'))})))
        (doseq [f (concat cljs exts' libs' incs')] (core/consume-file! f))))))
