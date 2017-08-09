(ns adzerk.boot-cljs.impl-test
  (:require [clojure.test :refer :all]
            [adzerk.boot-cljs.impl :refer :all]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(defn delete-dir [dir]
  (doseq [f (file-seq dir)
          :when (.isFile f)]
    (.delete f))
  (doseq [f (file-seq dir)
          :when (.isDirectory f)]
    (.delete f))
  (.delete dir))

(deftest handle-ex-test
  (with-redefs [adzerk.boot-cljs.impl/new-boot? true]
    (let [source-dir (io/file "boot-cljs-handle-ex-test")
          core-cljs-file (io/file source-dir "frontend" "core.cljs")]
      (delete-dir source-dir)

      ;; Setup mock source for finding the original source path
      (io/make-parents core-cljs-file)
      (spit core-cljs-file "")

      (testing "old error format"
        (let [e (handle-ex (ex-info "Unmatched delimiter )"
                                    {:type :reader-exception
                                     :file "/home/juho/.boot/cache/tmp/home/juho/Source/saapas/obh/uanrg/frontend/core.cljs"
                                     :line 30
                                     :column 67})
                           [(.getPath source-dir)]
                           ["/home/juho/.boot/cache/tmp/home/juho/Source/saapas/obh/uanrg"])]
          (is (= "boot-cljs-handle-ex-test/frontend/core.cljs [line 30, col 67] Unmatched delimiter )" (.getMessage e)))
          (is (= "boot-cljs-handle-ex-test/frontend/core.cljs" (:file (ex-data e))))))

      (testing "new error format"
        (let [e (handle-ex (ex-info "/home/juho/.boot/cache/tmp/home/juho/Source/saapas/obh/uanrg/frontend/core.cljs [line 30, col 67] Unmatched delimiter )."
                                    {:type :reader-exception
                                     :ex-kind :reader-error
                                     :file "/home/juho/.boot/cache/tmp/home/juho/Source/saapas/obh/uanrg/frontend/core.cljs"
                                     :line 30
                                     :col 67})
                           [(.getPath source-dir)]
                           ["/home/juho/.boot/cache/tmp/home/juho/Source/saapas/obh/uanrg"])]
          (is (= "boot-cljs-handle-ex-test/frontend/core.cljs [line 30, col 67] Unmatched delimiter )." (.getMessage e)))
          (is (= "boot-cljs-handle-ex-test/frontend/core.cljs" (:file (ex-data e))))))

      (delete-dir source-dir))))
