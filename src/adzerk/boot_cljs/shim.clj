(ns adzerk.boot-cljs.shim
  (:require [clojure.java.io    :as io]
            [boot.file          :as file]))

(defn- path->js
  [path]
  (-> path
      (.replaceAll "\\.cljs$" "")
      (.replaceAll "[/\\\\]" ".")))

(defn path->ns
  [path]
  (-> (path->js path) (.replaceAll "_" "-")))

(defn- file->goog
  [path]
  (format "goog.require('%s');" (path->js path)))

(defn- write-src
  [inc]
  (format "writeScript(\"<script src='\" + prefix + \"%s'></script>\");\n" inc))

(defn- write-body
  [code]
  (format "writeScript(\"<script>%s</script>\");\n" code))

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
  var loadedSrcs = {};
  var scripts = document.getElementsByTagName('script');
  for (var i = 0; i < scripts.length; i++) {
    if (scripts[i].src !== undefined) {
      loadedSrcs[scripts[i].src] = true;
    }
  }
  function writeScript(src) {
    var newElem;
    if (window.__boot_cljs_shim_loaded === undefined) {
      document.write(src);
    } else {
      newElem = document.createElement('div');
      newElem.innerHTML = src;
      if (newElem.src !== undefined && loaded[newElem.src] === undefined) {
        document.getElementsByTagName('head')[0].appendChild(newElem);
      }
    }
  }
%s%s
window.__boot_cljs_shim_loaded = true;
})();
")

(defn write-shim!
  [f shim-path incs cljs output-path output-dir]
  (let [shim-dir (.getParentFile (io/file shim-path))
        scripts  (-> incs
                     (->> (mapv io/file))
                     (conj (io/file output-dir "goog" "base.js"))
                     (conj output-path)
                     (->> (mapv (partial file/relative-to shim-dir))))]
    (spit f (format shim-js
                    (.getName f)
                    (apply str (map write-src scripts))
                    (write-body (apply str (sort (map file->goog cljs))))))))
