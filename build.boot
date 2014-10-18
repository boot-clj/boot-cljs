(set-env!
  :src-paths    #{"src"}
  :repositories #(conj % ["deploy" {:url      "https://clojars.org/repo"
                                    :username (System/getenv "CLOJARS_USER")
                                    :password (System/getenv "CLOJARS_PASS")}])
  :dependencies '[[org.clojure/clojure       "1.6.0"      :scope "provided"]
                  [boot/core                 "2.0.0-pre5" :scope "provided"]
                  [org.clojure/clojurescript "0.0-2371"   :scope "test"]])

(task-options!
  pom [:project     'tailrecursion/boot-cljs
       :version     "0.0-2371-0-SNAPSHOT"
       :description "Boot task to compile ClojureScript applications."
       :url         "https://github.com/tailrecursion/boot-cljs"
       :scm         {:url "https://github.com/tailrecursion/boot-cljs"}
       :license     {:name "Eclipse Public License"
                     :url  "http://www.eclipse.org/legal/epl-v10.html"}])

(deftask build
  "Build jar and install to local repo."
  []
  (comp (pom) (add-src) (jar) (install)))
