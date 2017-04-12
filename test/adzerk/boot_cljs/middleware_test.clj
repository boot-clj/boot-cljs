(ns adzerk.boot-cljs.middleware-test
  (:require [clojure.test :refer :all]
            [adzerk.boot-cljs.middleware :refer :all]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(deftest compiler-options-test
  (testing "without metadata"
    (is (= {:closure-defines {"a" 1
                              "b" 2}}
           (:opts (compiler-options
                    {:main {:compiler-options {:closure-defines {"a" 1}}}}
                    {:compiler-options {:closure-defines {"b" 2}}})))))

  ;; FIXME: it should be possible to remove value (set to nil)
  ;; using right value
  (testing "without metadata, vector on left and nil on right"
    (is (= {:preloads nil}
           (:opts (compiler-options
                    {:main {:compiler-options {:preloads ['foo.bar]}}}
                    {:compiler-options {:preloads nil}})))))

  (testing ":merge on left and :replace on right"
    (is (= {:closure-defines {"b" 2}}
           (:opts (compiler-options
                    {:main {:compiler-options {:closure-defines ^:merge {"a" 1}}}}
                    {:compiler-options {:closure-defines ^:replace {"b" 2}}}))))) )

