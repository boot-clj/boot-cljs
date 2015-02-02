(set-env!
  ;; using the sonatype repo is sometimes useful when testing Clojurescript
  ;; versions that not yet propagated to Clojars
  ;; :repositories #(conj % '["sonatype" {:url "http://oss.sonatype.org/content/repositories/releases"}])
  :dependencies '[[org.clojure/clojure       "1.6.0"     :scope "provided"]
                  [boot/core                 "2.0.0-rc6" :scope "provided"]
                  [adzerk/bootlaces          "0.1.9"     :scope "test"]
                  [org.clojure/clojurescript "0.0-2760"  :scope "test"]])

(require '[adzerk.bootlaces :refer :all])

(def +version+ "0.0-2760-0")

(bootlaces! +version+)

(task-options!
 pom  {:project     'adzerk/boot-cljs
       :version     +version+
       :description "Boot task to compile ClojureScript applications."
       :url         "https://github.com/adzerk/boot-cljs"
       :scm         {:url "https://github.com/adzerk/boot-cljs"}
       :license     {"Eclipse Public License" "http://www.eclipse.org/legal/epl-v10.html"}})
