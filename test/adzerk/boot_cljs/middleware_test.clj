(ns adzerk.boot-cljs.middleware-test
  (:require [clojure.test :refer :all]
            [adzerk.boot-cljs.middleware :refer :all]
            [clojure.java.io :as io]
            [clojure.stacktrace :as st]
            [clojure.string :as string]))

(deftest source-map-test
  (is (= {:opts {:source-map true}}
         (source-map {:opts {:source-map true}})))

  (is (= {:opts {:source-map true
                 :optimizations :none}}
         (source-map {:opts {:source-map true
                             :optimizations :none}})))

  (testing "source-map boolean is replaced with path when doing optimized build"
    (is (= {:opts {:source-map "main.js.map"
                   :output-to "main.js"
                   :optimizations :advanced}}
           (source-map {:opts {:source-map true
                               :optimizations :advanced
                               :output-to "main.js"}}))))

  (testing "if source-map disabled, not path added for optimized builds"
    (is (= {:opts {:source-map false
                   :output-to "main.js"
                   :optimizations :advanced}}
           (source-map {:opts {:source-map false
                               :optimizations :advanced
                               :output-to "main.js"}})))

    (is (= {:opts {:source-map false
                   :output-to "main.js"
                   :optimizations :simple}}
           (source-map {:opts {:source-map false
                               :optimizations :simple
                               :output-to "main.js"}}))))

  (testing "if source-map path is provided, use that"
    (is (= {:opts {:source-map "hello.js.map"
                   :output-to "main.js"
                   :optimizations :simple}}
           (source-map {:opts {:source-map "hello.js.map"
                               :optimizations :simple
                               :output-to "main.js"}})))))
