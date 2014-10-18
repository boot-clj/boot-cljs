(ns tailrecursion.boot-cljs.impl
  (:require
   [clojure.java.io :as io]
   [boot.pod        :as pod]
   [cljs.env        :as env]
   [cljs.closure    :as cljs])
  (:import
   [java.net URL]))

(defn jarfile-for
  [url]
  (-> url .getPath (.replaceAll "![^!]+$" "") URL. .toURI io/file))

(def dep-jars-on-cp
  (memoize
    (fn [env marker]
      (->> marker
        pod/resources
        (filter #(= "jar" (.getProtocol %)))
        (map jarfile-for)))))

(defn in-dep-order
  [env jars]
  (let [jars-set (set jars)]
    (->> (pod/jars-in-dep-order env)
      (filter (partial contains? jars-set)))))

(def files-in-jar
  (memoize
    (fn [jarfile marker & [file-exts]]
      (->> jarfile
        pod/jar-entries
        (filter (fn [[p u]] (and (.startsWith p marker)
                              (or (empty? file-exts)
                                (some #(.endsWith p %) file-exts)))))))))

(defn dep-files
  [env marker & [file-exts]]
  (->> marker
    (dep-jars-on-cp env)
    (in-dep-order env)
    (mapcat #(files-in-jar % marker file-exts))))

(defn strip-marker
  [marker dep-files]
  (->> dep-files (map (fn [[p u]] [(.replaceAll p (str "^" marker) "") u]))))

(defn group-by-exts
  [exts dep-files]
  (->> dep-files (group-by (fn [[p u]] (some #(and (.endsWith p %) %) exts)))))

(defn cljs-dep-files
  [env]
  (let [marker "hoplon/include/"
        exts   [".inc.js" ".lib.js" ".ext.js"]]
    (->> (dep-files env marker exts)
      (strip-marker marker)
      (group-by-exts exts))))

(defn install-dep-files
  [env inc-dir ext-dir lib-dir]
  (let [{incs ".inc.js"
         libs ".lib.js"
         exts ".ext.js"} (cljs-dep-files env)
        copy  #(let [f (io/file %1 %2)] (->> f (pod/copy-url %3) .getPath))]
    {:incs (for [x incs] (apply copy inc-dir x))
     :exts (for [x exts] (apply copy ext-dir x))
     :libs (for [x libs] (apply copy lib-dir x))}))

(defrecord CljsSourcePaths [paths]
  cljs/Compilable
  (-compile [this opts]
    (mapcat #(cljs/-compile % opts) paths)))

(def ^:private stored-env (atom nil))

(defn cljs-env [opts]
  (compare-and-set! stored-env nil (env/default-compiler-env opts))
  @stored-env)

(defn compile-cljs
  [src-paths {:keys [output-to] :as opts}]
  (binding [env/*compiler* (cljs-env opts)]
    (cljs/build (CljsSourcePaths. (filter #(.exists (io/file %)) src-paths)) opts)))
