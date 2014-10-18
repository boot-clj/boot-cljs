(set-env!
  :src-paths    #{"src"}
  :repositories #(conj % ["deploy" {:url      "https://clojars.org/repo"
                                    :username (System/getenv "CLOJARS_USER")
                                    :password (System/getenv "CLOJARS_PASS")}])
  :dependencies '[[org.clojure/clojure       "1.6.0"      :scope "provided"]
                  [boot/core                 "2.0.0-pre5" :scope "provided"]
                  [org.clojure/clojurescript "0.0-2371"   :scope "test"]])

(require '[boot.git :as git])

(def +VERSION+ "0.0-2371-1")

(task-options!
  push [:repo           "deploy"
        :ensure-branch  "master"
        :ensure-clean   true
        :ensure-tag     (git/last-commit)
        :ensure-version +VERSION+]
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

(deftask push-snapshot
  "Deploy snapshot version to Clojars."
  [f file PATH str "The jar file to deploy."]
  (push :file file :ensure-snapshot true))

(deftask push-release
  "Deploy release version to Clojars."
  [f file PATH str "The jar file to deploy."]
  (push :file file :tag true :ensure-release true))
