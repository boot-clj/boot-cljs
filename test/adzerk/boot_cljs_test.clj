(ns adzerk.boot-cljs-test
  (:require [boot.pod :as pod]
            [clojure.test :refer :all]
            [adzerk.boot-cljs :refer [cljs]]
            [metosin.boot-alt-http :refer [serve]]
            [boot.tmpdir :as tmpdir]
            [clojure.java.io :as io])
  (:import [java.net ServerSocket]
           [java.nio.file Files]
           [java.net URL URLClassLoader]
           [java.util.concurrent Executors ThreadFactory]
           [java.util.concurrent.atomic AtomicLong]))

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
  [port path]
  (pod/with-eval-in @hunit-pod
    (.asText (.getPage client ~(format "http://localhost:%s%s" port path)))))

(defn wait [p]
  (fn middleware [next-handler]
    (fn handler [fileset]
      (next-handler fileset)
      (deref p 15000 nil))))

(defn test-pipeline [port p]
  (comp
    (serve :port port :prefixes #{""})
    (cljs)
    (wait p)))

(defn free-port []
  (let [s (ServerSocket. 0)
        p (.getLocalPort s)]
    (.close s)
    p))

(defn mock-fileset
  [sources]
  (let [tmp (Files/createTempDirectory "mock-fileset" (make-array java.nio.file.attribute.FileAttribute 0))
        resource-dir (doto (io/file tmp "resources") .mkdirs)
        resource-tempdir (tmpdir/map->TmpDir {:dir resource-dir
                                              :input true
                                              :output true})
        fileset (tmpdir/map->TmpFileSet
                  {:dirs #{resource-tempdir}
                   :tree {}
                   :blob (doto (io/file tmp "blob") .mkdirs)
                   :scratch (doto (io/file tmp "scratch") .mkdirs)})]
    (reduce (fn [fileset source-dir]
              (tmpdir/add fileset resource-dir source-dir {}))
            fileset
            sources)))

(defn new-thread-factory [name]
  (let [cnt (AtomicLong. 0)]
    (reify ThreadFactory
      (^Thread newThread [this ^Runnable runnable]
        (doto (Thread. runnable)
          (.setName (format name (.getAndIncrement cnt)))))) ))

(defn make-pod-hack
  ([e] (#'boot.pod/init-pod! e (boot.App/newPod nil boot.pod/data)))
  ([e env & {:as m}]
   (boot.pod/make-pod env m)))

(defn mock-boot [pipeline fileset]
  (let [org-cl (.getContextClassLoader (Thread/currentThread))
        org-agent-solo-executor clojure.lang.Agent/soloExecutor
        org-agent-pooled-executor clojure.lang.Agent/pooledExecutor
        org-env boot.pod/env]
    (try
      ;; setup
      (.setContextClassLoader (Thread/currentThread) (URLClassLoader/newInstance (into-array URL (map #(.toURL (.toURI (:dir %))) (:dirs fileset))) (.getContextClassLoader (Thread/currentThread))))
      (set! clojure.lang.Agent/soloExecutor (Executors/newCachedThreadPool (new-thread-factory "clojure-agent-send-off-pool-%d")))
      (set! clojure.lang.Agent/pooledExecutor (Executors/newFixedThreadPool (+ 2 (.availableProcessors (Runtime/getRuntime))) (new-thread-factory "clojure-agent-send-pool-%d")))

      (println (map #(.toURL (.toURI (:dir %))) (:dirs fileset)))
      (println (map #(.getPath (:dir %)) (:dirs fileset)))
      (println "direct" (io/resource "demo/core.cljs"))
      @(future (println "future" (io/resource "demo/core.cljs")))

      (alter-var-root #'boot.pod/env (constantly (assoc boot.pod/env :directories (map #(.getPath (:dir %)) (:dirs fileset)))))

      ((pipeline identity) fileset)

      ;; todo: remove dirs

      (finally
        (alter-var-root #'boot.pod/env org-env)
        (clojure.lang.Agent/shutdown)
        (set! clojure.lang.Agent/soloExecutor org-agent-solo-executor)
        (set! clojure.lang.Agent/pooledExecutor org-agent-pooled-executor)
        (.setContextClassLoader (Thread/currentThread) org-cl)))))

(comment
  (let [org-agent-solo-executor clojure.lang.Agent/soloExecutor
        org-env boot.pod/env]
    (try
      (.setContextClassLoader (Thread/currentThread) (URLClassLoader/newInstance (into-array URL (map #(.toURL (.toURI %)) [(io/file "test-files" "a-test")])) (.getContextClassLoader (Thread/currentThread))))
      (set! clojure.lang.Agent/soloExecutor (Executors/newCachedThreadPool (new-thread-factory "clojure-agent-send-off-pool-%d")))
      (println "direct" (io/resource "demo/core.cljs"))
      @(future (println "future" (io/resource "demo/core.cljs")))
      (alter-var-root #'boot.pod/env (constantly (assoc boot.pod/env :directories (map #(.getPath %) [(io/file "test-files" "a-test")]))) )
      (let [p (boot.pod/make-pod)]
        (boot.pod/with-eval-in p
          (println "pod" (clojure.java.io/resource "demo/core.cljs")))
        (boot.pod/destroy-pod p))
      (finally
        (alter-var-root #'boot.pod/env org-env)
        (set! clojure.lang.Agent/soloExecutor org-agent-solo-executor)))))

(deftest a-test
  (testing "Compiling with .cljs.edn"
    (let [port (free-port)
          p (promise)
          fileset (mock-fileset [(io/file "test-files" "a-test")])]
      (mock-boot (test-pipeline port p) fileset)
      (is (= "test passed" (hunit-page port "/demo/index.html")))
      (deliver p true))))
