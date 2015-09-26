(ns adzerk.boot-cljs.util-test
  (:require [clojure.test :refer :all]
            [adzerk.boot-cljs.util :refer :all]
            [clojure.java.io :as io]))

(deftest find-relative-path-test
  (is (= "src/foo/bar.clj" (find-relative-path ["/foo" "/bar"] "/foo/src/foo/bar.clj")))
  (is (= nil (find-relative-path ["/foo" "/bar"] nil))))

(def pwd (.getAbsolutePath (io/file "")))

(deftest find-original-path-test
  (testing "file doesn't exist in source-paths - returns relative path"
    (is (= "foo/bar.clj" (find-original-path ["src" "test"] ["/foo" "/bar"] "/foo/foo/bar.clj"))))
  (testing "existing file - returns path with original source-path"
    (is (= "src/adzerk/boot_cljs.clj" (find-original-path ["src" "test"] ["/foo" "/bar"] "/foo/adzerk/boot_cljs.clj")))))
