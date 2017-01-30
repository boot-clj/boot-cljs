(set-env!
  :resource-paths #{"src"}
  :source-paths #{"test"}
  :dependencies   '[[org.clojure/clojure "1.8.0" :scope "provided"]
                    [metosin/boot-alt-test "0.3.0" :scope "test"]
                    [metosin/boot-alt-http "0.1.2-SNAPSHOT" :scope "test"]
                    [org.clojure/clojurescript "1.7.228" :scope "test"]
                    [ns-tracker "0.3.1" :scope "test"]])

(require '[metosin.boot-alt-test :refer [alt-test]]
         '[adzerk.boot-cljs :refer [cljs]]
         '[metosin.boot-alt-http :refer [serve]])

(def +version+ "2.0.0-SNAPSHOT")

(task-options!
  pom {:project     'adzerk/boot-cljs
       :version     +version+
       :description "Boot task to compile ClojureScript applications."
       :url         "https://github.com/adzerk/boot-cljs"
       :scm         {:url "https://github.com/adzerk/boot-cljs"}
       :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})

(deftask run-tests
  [O optimizations   LEVEL kw  "Compiler optimization level."]
  (comp (alt-test :report 'eftest.report.pretty/report)))

(deftask build []
  (comp
   (pom)
   (jar)
   (install)))

(deftask dev []
  (comp
    (watch)
    (build)
    (repl :server true)))

(deftask deploy []
  (comp
   (build)
   (push :repo "clojars" :gpg-sign (not (.endsWith +version+ "-SNAPSHOT")))))
