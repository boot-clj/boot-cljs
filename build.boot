(set-env!
  :dependencies '[[org.clojure/clojure       "1.6.0"      :scope "provided"]
                  [boot/core                 "2.0.0-pre9" :scope "provided"]
                  [enlive                    "1.1.5"      :scope "test"]
                  [tailrecursion/boot-useful "0.1.3"      :scope "test"]
                  [org.clojure/clojurescript "0.0-2371"   :scope "test"]])

(require '[tailrecursion.boot-useful :refer :all])

(def +version+ "0.0-2371-25")

(useful! +version+)

(task-options!
  pom  [:project     'adzerk/boot-cljs
        :version     +version+
        :description "Boot task to compile ClojureScript applications."
        :url         "https://github.com/adzerk/boot-cljs"
        :scm         {:url "https://github.com/adzerk/boot-cljs"}
        :license     {:name "Eclipse Public License"
                      :url  "http://www.eclipse.org/legal/epl-v10.html"}])
