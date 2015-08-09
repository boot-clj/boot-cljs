(set-env!
  :resource-paths #{"src"}
  :source-paths   #{"test"}
  :dependencies   '[[org.clojure/clojure       "1.7.0"      :scope "provided"]
                    [boot/core                 "2.0.0"      :scope "provided"]
                    [adzerk/bootlaces          "0.1.11"     :scope "test"]
                    [adzerk/boot-test          "1.0.4"      :scope "test"]
                    [org.clojure/clojurescript "1.7.48"     :scope "test"]
                    [ns-tracker                "0.3.0"      :scope "test"]])

(require '[adzerk.bootlaces :refer :all]
         '[adzerk.boot-test :refer :all])

(def +version+ "1.7.48-SNAPSHOT")

(bootlaces! +version+)

(task-options!
  pom {:project     'adzerk/boot-cljs
       :version     +version+
       :description "Boot task to compile ClojureScript applications."
       :url         "https://github.com/adzerk/boot-cljs"
       :scm         {:url "https://github.com/adzerk/boot-cljs"}
       :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})
