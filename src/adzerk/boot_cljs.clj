(ns adzerk.boot-cljs
  {:boot/export-tasks true}
  (:require [clojure.java.io       :as io]
            [boot.from.backtick    :as bt]
            [clojure.set           :as set]
            [clojure.string        :as string]
            [boot.pod              :as pod]
            [boot.core             :as core]
            [boot.file             :as file]
            [boot.util             :as util]
            [adzerk.boot-cljs.shim :as shim]))

(def ^:private deps
  '[[org.clojure/clojurescript "0.0-2629"]])

(defn- replace-path
  [path name]
  (if-let [p (.getParent (io/file path))]
    (io/file p name)
    (io/file name)))

(defn- output-path-for
  [f]
  (let [fname (str "boot-cljs-" (.getName (io/file f)))]
    (replace-path f fname)))

(defn- cljs-opts!
  [{:keys [output-to optimizations source-map unified-mode] :as task-opts}
   {:keys [output-dir] :as compiler-opts}]
  (if (and unified-mode (not= optimizations :none))
    (util/warn "unified-mode on; setting optimizations to :none\n"))
  (let [optimizations (if unified-mode :none optimizations)
        output-dir    (or output-dir "out")
        output-path*  (or output-to "main.js")
        output-path   (if-not unified-mode
                        output-path*
                        (output-path-for output-path*))
        tmp-dir       (core/temp-dir!)
        js-out        (io/file tmp-dir output-path)
        shim          (io/file tmp-dir output-path*)
        smap          (io/file tmp-dir (str output-path ".map"))
        js-parent     (str (.getParent (io/file output-path)))
        none?         (= :none optimizations)
        keep-out?     (or source-map none?)
        out-dir       (if-not keep-out?
                        (core/temp-dir!)
                        (apply io/file tmp-dir (remove empty? [js-parent output-dir])))
        base-opts     {:libs          []
                       :externs       []
                       :preamble      []
                       :foreign-libs  []
                       :output-to     (.getPath js-out)
                       :output-dir    (.getPath out-dir)
                       :optimizations (or optimizations :whitespace)}
        ;; src-map: see https://github.com/clojure/clojurescript/wiki/Source-maps
        smap-opts   {:source-map-path (if none? js-parent output-dir)
                     :source-map      (.getPath (if none? (file/relative-to tmp-dir smap) smap))}]
    {:none?       none?
     :js-out      js-out
     :shim        shim
     :shim-path   output-path*
     :tmp-dir     tmp-dir
     :output-dir  output-dir
     :output-path output-path
     :cljs-opts   (merge base-opts
                         (when source-map smap-opts)
                         (dissoc compiler-opts :output-dir :optimizations))}))

(defn- write-main-cljs!
  [main-dir main]
  (when main
    (let [file      (core/tmpfile main)
          path      (io/file (core/tmppath main))
          name      (-> file .getName (.replaceAll "\\.main\\.edn$" ""))
          js-out    (.getPath (replace-path path (str name ".js")))
          cljs-out  (.getPath (replace-path path (str name ".cljs")))
          cljs-file (.getPath (io/file main-dir cljs-out))
          cljs-ns   (symbol (shim/path->ns cljs-out))
          main-edn  (read-string (slurp file))
          init-fns  (:init-fns main-edn)
          requires  (set (:require main-edn))
          init-nss  (sort (into requires (map (comp symbol namespace) init-fns)))]
      (io/make-parents (io/file cljs-file))
      (->> [(bt/template
              (ns ~cljs-ns
                (:require ~@init-nss)))
            (bt/template
              (do ~@(map list init-fns)))]
           (map pr-str)
           (string/join "\n\n")
           (spit cljs-file))
      {:main-cljs cljs-file :main-path cljs-out :main-js js-out})))

(defn- sort-inc-js
  [x y]
  (let [[dx dy] (map :dependency-order [x y])
        [nx ny] (map core/tmppath [x y])]
    (cond (and dx dy) (compare dx dy)
          (or dx dy)  (- (compare dx dy))
          :else       (compare nx ny))))

(defn- max* [& args] (apply max (or (seq args) [0])))

(defn- start-dep-order
  [maps]
  (->> maps (keep :dependency-order) (apply max*) inc))

(defn- inc-js-dep-meta
  "Note: tmpfiles expected to be sorted already"
  [fileset tmpfiles]
  (let [start (->> fileset core/ls start-dep-order)]
    (->> tmpfiles
         (map-indexed #(do [(core/tmppath %2) {:dependency-order (+ start %1)}]))
         (into {}))))

(defn- add-compiled-js-dep-meta
  "Note: paths expected to be sorted already"
  [js-dep-meta paths]
  (let [start (->> js-dep-meta vals start-dep-order)]
    (->> paths
         (map-indexed #(do [%2 {:dependency-order (+ start %1)}]))
         (into js-dep-meta))))

(core/deftask cljs
  "Compile ClojureScript applications.

  The default --output-path is 'main.js', but if a <name>.main.edn file exists
  in the fileset the JS will be compiled to <name>.js and the --output-path
  options will be ignored.

  Available --optimization levels (default 'whitespace'):

  * none         No optimizations. Bypass the Closure compiler completely.
  * whitespace   Remove comments, unnecessary whitespace, and punctuation.
  * simple       Whitespace + local variable and function parameter renaming.
  * advanced     Simple + aggressive renaming, inlining, dead code elimination, etc.

  The --compiler-options option can be used to set any other options that should
  be passed to the Clojurescript compiler. A full list of options can be found here:
  https://github.com/clojure/clojurescript/wiki/Compiler-Options.
  Passing an :optimizations key in this map will have no effects as this
  is handled by the --optimizations task option.

  The --unified-mode option automates the process of adding the necessary <script>
  tags to HTML files when compiling with optimizations=none. When enabled, any HTML
  file that loads the output-to JS file via a script tag (as when compiling with
  optimizations) will have the base.js and goog.require() <script> tags added
  automatically."

  [o output-to PATH        str  "The output js file path relative to docroot."
   O optimizations LEVEL   kw   "The optimization level."
   s source-map            bool "Create source map for compiled JS."
   c compiler-options OPTS edn  "Options to pass to the Clojurescript compiler"
   u unified-mode          bool "Automatically add <script> tags when optimizations=none."]

  (let [main-dir   (core/temp-dir!)
        config     (atom nil)
        saved-cljs (atom nil)
        pod-env    (-> (core/get-env) (update-in [:dependencies] into deps))
        p          (pod/pod-pool pod-env)]
    (core/with-pre-wrap fileset
      (let [srcs      (core/input-files fileset)
            ->path    (comp (memfn getPath) core/tmpfile)
            main      (->> srcs (core/by-ext [".main.edn"]) first (write-main-cljs! main-dir))
            incs      (->> srcs (core/by-ext [".inc.js"]) (sort sort-inc-js))
            inc-urls  (->> incs (mapv core/tmppath))
            cljs      (if main
                        [(:main-path main)]
                        (->> srcs (core/by-ext [".cljs"]) (mapv core/tmppath)))
            exts      (->> srcs (core/by-ext [".ext.js"]) (mapv ->path))
            libs      (->> srcs (core/by-ext [".lib.js"]) (mapv ->path))
            opts      (assoc *opts* :output-to (or (:main-js main) output-to))
            {:keys [tmp-dir js-out cljs-opts none? shim shim-path output-path output-dir]}
            (or @config (reset! config (cljs-opts! opts compiler-options)))
            cljs-opts (merge-with into cljs-opts {:libs     libs
                                                  :externs  exts
                                                  :preamble (if none? [] inc-urls)})
            sources   (if main
                        [(.getPath main-dir)]
                        (mapv #(.getPath %) (core/input-dirs fileset)))]
        (io/make-parents js-out)
        (util/info "Compiling %s...\n" (.getName js-out))
        (let [{:keys [warnings dep-order]}
              (pod/with-call-in (p)
                (adzerk.boot-cljs.impl/compile-cljs ~sources ~cljs-opts))
              dep-order      (concat dep-order [shim-path])
              dep-order-meta (-> fileset
                                 (inc-js-dep-meta incs)
                                 (add-compiled-js-dep-meta dep-order))]
          (swap! core/*warnings* + (or warnings 0))
          (when unified-mode
            (shim/write-shim! shim shim-path inc-urls cljs output-path
                              (replace-path shim-path output-dir)))
          (-> fileset
              (core/mv-resource (when none? incs))
              (core/add-resource tmp-dir)
              (core/add-meta dep-order-meta)
              core/commit!))))))
