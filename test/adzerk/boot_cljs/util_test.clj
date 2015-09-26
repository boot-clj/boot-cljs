(ns adzerk.boot-cljs.util-test
  (:require [clojure.test :refer :all]
            [adzerk.boot-cljs.util :refer :all]
            [clojure.java.io :as io]
            [clojure.stacktrace :as st]
            [clojure.string :as string]))

(deftest find-relative-path-test
  (is (= "src/foo/bar.clj" (find-relative-path ["/foo" "/bar"] "/foo/src/foo/bar.clj")))
  (is (= nil (find-relative-path ["/foo" "/bar"] nil))))

(def pwd (.getAbsolutePath (io/file "")))

(deftest find-original-path-test
  (testing "file doesn't exist in source-paths - returns relative path"
    (is (= "foo/bar.clj" (find-original-path ["src" "test"] ["/foo" "/bar"] "/foo/foo/bar.clj"))))
  (testing "existing file - returns path with original source-path"
    (is (= "src/adzerk/boot_cljs.clj" (find-original-path ["src" "test"] ["/foo" "/bar"] "/foo/adzerk/boot_cljs.clj")))))

(deftest basic-exception-test
  (let [original (Exception. "foo")
        a (deserialize-exception (serialize-exception original))]
    (is (= "foo" (.getMessage a)))))

(deftest ex-data-test
  (let [original (ex-info "foo" {:a 1})
        a (deserialize-exception (serialize-exception original))]
    (is (= "foo" (.getMessage a)))
    (is (= {:a 1} (ex-data a)))))

(deftest cause-test
  (let [original (Exception. "foo" (Exception. "bar"))
        a (deserialize-exception (serialize-exception original))]
    (is (= "bar" (.getMessage (.getCause a))))))

(defn drop-first-line [s]
  (-> s
      (string/split #"\n")
      rest
      (->> (string/join "\n"))))

(deftest stack-trace-test
  ; First line differs as it contains the name of Exception class
  (let [original (Exception. "foo")
        a (deserialize-exception (serialize-exception original))]
    (is (= (drop-first-line (with-out-str (st/print-stack-trace original)))
           (drop-first-line (with-out-str (st/print-stack-trace a)))))))
