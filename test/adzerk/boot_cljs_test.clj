(ns adzerk.boot-cljs-test
  (:require
    [boot.pod :as pod]
    [clojure.test :refer :all]))

(def pod-env
  '{:dependencies [[net.sourceforge.htmlunit/htmlunit "2.18"]]})

(def hunit-pod
  (delay (doto (pod/make-pod pod-env)
           (pod/with-eval-in
             (import
               [java.util.logging Logger Level]
               [com.gargoylesoftware.htmlunit WebClient])
             (-> (Logger/getLogger "com.gargoylesoftware")
                 (.setLevel (Level/-OFF)))
             (def client (WebClient.))))))

(defn hunit-page
  [path]
  (pod/with-eval-in @hunit-pod
    (.asText (.getPage client ~(format "http://localhost:3000%s" path)))))

(deftest a-test
  (testing "Compiling with .cljs.edn"
    (is (= "test passed" (hunit-page "/demo/index.html")))))

(deftest npm-test
  (testing "Includes npm modules when present"
    (is (= "test passed 00042" (hunit-page "/demo/other.html")))))
