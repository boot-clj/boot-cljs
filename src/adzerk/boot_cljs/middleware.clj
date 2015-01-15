(ns adzerk.boot-cljs.middleware
  (:require
    [clojure.java.io          :as io]
    [boot.from.backtick       :as bt]
    [clojure.string           :as string]
    [boot.pod                 :as pod]
    [boot.file                :as file]
    [boot.core                :as core]
    [adzerk.boot-cljs.util    :as util]
    [adzerk.boot-cljs.js-deps :as deps]))

;; helpers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- main-ns-forms
  "Given a namespace symbol, ns-sym, a sorted set of namespace symbols this
  namespace will :require, requires, and a vector of namespace-qualified
  symbols, init-fns, constructs the forms for a namespace named ns-sym that
  :requires the namespaces in requires and calls the init-fns with no arguments
  at the top level."
  [ns-sym requires init-fns]
  [(bt/template
     (ns ~ns-sym
       (:require ~@requires)))
   (bt/template
     (do ~@(map list init-fns)))])

(defn- format-ns-forms
  "Given a sequence of forms, formats them nicely as a string."
  [forms]
  (->> forms (map pr-str) (string/join "\n\n") (format "%s\n")))

(defn- file->goog
  "Given a CLJS source file, returns the corresponding goog.require() JS expr."
  [path]
  (format "goog.require('%s');" (util/path->js path)))

(defn- write-src
  "Returns a JS expr that uses writeScript() to add a <script> tag to the
  document with the given src attribute and no body."
  [src]
  (format "  writeScript(\"<script src='\" + prefix + \"%s'></script>\");\n" src))

(defn- write-body
  "Returns a JS expr that uses writeScript() to add a <script> tag to the
  document with the given body and no src attribute."
  [body]
  (format "  writeScript(\"<script>%s</script>\");\n" body))

(def ^:private shim-js
"// boot-cljs shim
(function() {
  var shimRegex = new RegExp('(.*)%s$');

  function findPrefix() {
    var els = document.getElementsByTagName('script');
    for (var i = 0; i < els.length; i++) {
      var src = els[i].getAttribute('src');
      var match = src && src.match(shimRegex);
      if (match) return match[1]; }
    return ''; }

  var prefix = findPrefix();
  var loadedSrcs = {};
  var scripts = document.getElementsByTagName('script');

  for (var i = 0; i < scripts.length; i++)
    if (scripts[i].src !== undefined)
      loadedSrcs[scripts[i].src] = true;

  function writeScript(src) {
    var newElem;
    if (window.__boot_cljs_shim_loaded === undefined)
      document.write(src);
    else {
      newElem = document.createElement('div');
      newElem.innerHTML = src;
      if (newElem.src !== undefined && loaded[newElem.src] === undefined) {
        document.getElementsByTagName('head')[0].appendChild(newElem); }}}

%s%s
  window.__boot_cljs_shim_loaded = true; })();
")

(defn- output-path-for
  "Given the path to the shim JS file, returns the corresponding output-to
  path to be given to the CLJS compiler. This is the JS file the shim will
  actually load."
  [shim-path]
  (->> shim-path util/get-name (str "boot-cljs-") (util/replace-path shim-path) .getPath))

;; middleware ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn main
  "Middleware to create the CLJS namespace for the build's .cljs.edn file and
  set the compiler :output-to option accordingly. The :output-to will be derived
  from the path of the .cljs.edn file (e.g. foo/bar.cljs.edn will produce the
  foo.bar CLJS namespace with output to foo/bar.js)."
  [{:keys [tmp-src tmp-out main files opts] :as ctx}]
  (let [[path file] ((juxt core/tmppath core/tmpfile) main)
        base-name   (-> file .getName deps/strip-extension)
        js-path     (.getPath (io/file tmp-out (str base-name ".js")))
        cljs-path   (.getPath (util/replace-path path (str base-name ".cljs")))
        cljs-file   (doto (io/file tmp-src cljs-path) io/make-parents)
        cljs-ns     (symbol (util/path->ns cljs-path))
        main-edn    (read-string (slurp file))
        init-fns    (:init-fns main-edn)
        requires    (into (sorted-set) (:require main-edn))
        init-nss    (into requires (map (comp symbol namespace) init-fns))]
    (->> (main-ns-forms cljs-ns init-nss init-fns) format-ns-forms (spit cljs-file))
    (assoc-in ctx [:opts :output-to] js-path)))

(defn shim
  "Middleware to create the JS shim that automatically loads goog/base.js and
  does the necessary goog.require()'s to bootstrap the compiled JS file when
  :optimizations is :none (i.e. when the Google Closure compiler is not being
  used to produce a single compiled JS file). When using any other compilation
  level this middleware adds external JS files to the compiler preamble and
  doesn't create a shim."
  [{:keys [tmp-src tmp-out main files opts docroot] :as ctx}]
  (if-not (= :none (:optimizations opts))
    (let [incs (->> (:incs files)
                    (map core/tmppath)
                    (remove #(contains? (set (:preamble opts)) %)))]
      (update-in ctx [:opts :preamble] into incs))
    (let [shim-path  (:output-to opts)
          shim-name  (util/get-name shim-path)
          output-to  (output-path-for shim-path)
          output-dir (util/get-name (:output-dir opts))
          cljs-paths (map core/tmppath (:cljs files))
          main*      (-> main core/tmppath deps/strip-extension)
          scripts    (-> (:incs files)
                         (->> (mapv #(util/rooted-relative docroot (core/tmppath %))))
                         (conj (io/file output-dir "goog" "base.js"))
                         (conj (util/get-name output-to)))]
      (->> (write-body (file->goog main*))
           (format shim-js shim-name (apply str (map write-src scripts)))
           (spit shim-path))
      (assoc-in ctx [:opts :output-to] output-to))))

(defn externs
  "Middleware to add externs files (i.e. files with the .ext.js extension) and
  Google Closure libs (i.e. files with the .lib.js extension) from the fileset
  to the CLJS compiler options."
  [{:keys [tmp-src tmp-out main files opts] :as ctx}]
  (let [exts (map core/tmppath (:exts files))
        libs (map core/tmppath (:libs files))]
    (update-in ctx [:opts] (partial merge-with into) {:libs libs :externs exts})))

(defn source-map
  "Middleware to configure source map related CLJS compiler options."
  [{:keys [tmp-src tmp-out main files docroot opts] :as ctx}]
  (if-not (:source-map opts)
    ctx
    (let [sm  (-> opts :output-to (str ".map"))
          dir (.getName (io/file (:output-dir opts)))]
      (update-in ctx [:opts] assoc :source-map sm :source-map-path dir))))
