(set-env!
  :resource-paths #{"src"}
  :source-paths #{"test"}
  :dependencies   '[[org.clojure/clojure       "1.7.0"  :scope "provided"]
                    [adzerk/boot-test          "1.1.0"  :scope "test"]
                    [pandeiro/boot-http        "0.7.0"  :scope "test"]
                    [org.clojure/clojurescript "1.7.228" :scope "test"]
                    [ns-tracker                "0.3.1"  :scope "test"]])

(require '[adzerk.boot-test   :refer [test]]
         '[adzerk.boot-cljs   :refer [cljs]]
         '[pandeiro.boot-http :refer [serve]])

(def +version+ "2.0.0-SNAPSHOT")

(task-options!
  pom {:project     'adzerk/boot-cljs
       :version     +version+
       :description "Boot task to compile ClojureScript applications."
       :url         "https://github.com/adzerk/boot-cljs"
       :scm         {:url "https://github.com/adzerk/boot-cljs"}
       :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})

(deftask run-tests
  [O optimizations   LEVEL kw  "Compiler optimization level."
   j junit-output-to JUNIT str "Test report destination."]
  (comp (serve)
        (cljs :optimizations (or optimizations :whitespace))
        (test :namespaces #{'adzerk.boot-cljs-test 'adzerk.boot-cljs.util-test}
              :junit-output-to junit-output-to)))

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
