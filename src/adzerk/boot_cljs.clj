(ns adzerk.boot-cljs
  {:boot/export-tasks true}
  (:require
   [clojure.java.io    :as io]
   [boot.from.backtick :as bt]
   [clojure.set        :as set]
   [clojure.string     :as string]
   [boot.pod           :as pod]
   [boot.core          :as core]
   [boot.file          :as file]
   [boot.util          :as util]))

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

(defn- path->js
  [path]
  (-> path
      (.replaceAll "\\.cljs$" "")
      (.replaceAll "[/\\\\]" ".")))

(defn- path->ns
  [path]
  (-> (path->js path) (.replaceAll "_" "-")))

(defn- file->goog
  [path]
  (format "goog.require('%s');" (path->js path)))

(defn- write-src
  [inc]
  (format "document.write(\"<script src='\" + prefix + \"%s'></script>\");\n" inc))

(defn- write-body
  [code]
  (format "document.write(\"<script>%s</script>\");\n" code))

(def ^:private shim-js
"// boot-cljs shim
(function() {
  var shimRegex = new RegExp('(.*)%s$');
  function findPrefix() {
    var els = document.getElementsByTagName('script');
    for (var i = 0; i < els.length; i++) {
      var src = els[i].getAttribute('src');
      var match = src && src.match(shimRegex);
      if (match) {
        return match[1];
      }
    }
    return '';
  }
  var prefix = findPrefix();
%s%s})();
")

(defn- write-shim!
  [f shim-path incs cljs output-path output-dir]
  (let [output-dir (replace-path shim-path output-dir)
        shim-dir (.getParentFile (io/file shim-path))
        scripts  (-> incs
                     (->> (mapv io/file))
                     (conj (io/file output-dir "goog" "base.js"))
                     (conj output-path)
                     (->> (mapv (partial relative-to shim-dir))))]
    (spit f (format shim-js
                    (.getName f)
                    (apply str (map write-src scripts))
                    (write-body (apply str (sort (map file->goog cljs))))))))

(defn- cljs-opts!
  [{:keys [output-dir node-target output-to optimizations
           pretty-print source-map no-warnings unified-mode]}]
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
                       :warnings      (not no-warnings)
                       :output-to     (.getPath js-out)
                       :output-dir    (.getPath out-dir)
                       :pretty-print  (boolean pretty-print)
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
                         (when node-target {:target :nodejs}))}))

(defn- write-main-cljs!
  [main-dir main]
  (when main
    (let [file      (core/tmpfile main)
          path      (io/file (core/tmppath main))
          name      (-> file .getName (.replaceAll "\\.main\\.edn$" ""))
          js-out    (.getPath (replace-path path (str name ".js")))
          cljs-out  (.getPath (replace-path path (str name ".cljs")))
          cljs-file (.getPath (io/file main-dir cljs-out))
          cljs-ns   (symbol (path->ns cljs-out))
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

  The --output-dir option is useful when using optimizations=none or when source
  maps are enabled. This option sets the name of the subdirectory (relative to
  the parent of the compiled JavaScript file) in which GClosure intermediate
  files will be written. The default name is 'out'.

  The --unified-mode option automates the process of adding the necessary <script>
  tags to HTML files when compiling with optimizations=none. When enabled, any HTML
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
   u unified-mode        bool "Automatically add <script> tags when optimizations=none."]

  (if (and unified-mode (not= optimizations :none))
    (util/warn "unified-mode on; setting optimizations to :none\n"))
  (let [main-dir   (core/temp-dir!)
        config     (atom nil)
        saved-cljs (atom nil)
        pod-env    (-> (core/get-env) (update-in [:dependencies] into deps))
        p          (pod/pod-pool pod-env)]
    (core/with-pre-wrap fileset
      (let [srcs      (core/input-files fileset)
            ->path    (comp (memfn getPath) core/tmpfile)
            main      (->> srcs (core/by-ext [".main.edn"]) first (write-main-cljs! main-dir))
            incs      (->> srcs (core/by-ext [".inc.js"]) (mapv core/tmppath) sort)
            cljs      (if main
                        [(:main-path main)]
                        (->> srcs (core/by-ext [".cljs"]) (mapv core/tmppath)))
            exts      (->> srcs (core/by-ext [".ext.js"]) (mapv ->path))
            libs      (->> srcs (core/by-ext [".lib.js"]) (mapv ->path))
            opts      (assoc *opts* :output-to (or (:main-js main) output-to))
            {:keys [tmp-dir js-out cljs-opts none? shim shim-path output-path output-dir]}
            (or @config (reset! config (cljs-opts! opts)))
            cljs-opts (merge-with into cljs-opts {:libs     libs
                                                  :externs  exts
                                                  :preamble (if none? [] incs)})
            sources   (if main
                        [(.getPath main-dir)]
                        (mapv #(.getPath %) (core/input-dirs fileset)))]
        (io/make-parents js-out)
        (util/info "Compiling %s...\n" (.getName js-out))
        (->> (-> (pod/with-call-in (p)
                   (adzerk.boot-cljs.impl/compile-cljs ~sources ~cljs-opts))
                 (get :warnings 0))
             (swap! core/*warnings* +))
        (when unified-mode
          (write-shim! shim shim-path incs cljs output-path output-dir))
        (-> fileset (core/add-resource tmp-dir) core/commit!)))))
