(set-env!
  :dependencies '[[org.clojure/clojure       "1.6.0"          :scope "provided"]
                  [boot/core                 "2.0.0-pre5"     :scope "provided"]
                  [tailrecursion/boot-useful "0.1.0-SNAPSHOT" :scope "test"]
                  [org.clojure/clojurescript "0.0-2371"       :scope "test"]])

(def +VERSION+ "0.0-2371-3")

(require '[tailrecursion.boot-useful :refer :all])

(task-options!
  pom  [:project        'tailrecursion/boot-cljs
        :version        +VERSION+
        :description    "Boot task to compile ClojureScript applications."
        :url            "https://github.com/tailrecursion/boot-cljs"
        :scm            {:url "https://github.com/tailrecursion/boot-cljs"}
        :license        {:name "Eclipse Public License"
                         :url  "http://www.eclipse.org/legal/epl-v10.html"}])

(deftask build
  "Build jar and install to local repo."
  []
  (comp (pom) (add-src) (jar) (install)))
