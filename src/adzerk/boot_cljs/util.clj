(ns adzerk.boot-cljs.util
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [boot.file :as file])
  (:import [java.util Base64]
           [java.io ObjectInputStream ObjectOutputStream ByteArrayOutputStream ByteArrayInputStream]))

(defn path->js
  "Given a path to a CLJS namespace source file, returns the corresponding
  Google Closure namespace name for goog.provide() or goog.require()."
  [path]
  (-> path
      (string/replace #"\.clj([s|c])?$" "")
      (string/replace #"[/\\]" ".")))

(defn path->ns
  "Given a path to a CLJS namespace source file, returns the corresponding
  CLJS namespace name."
  [path]
  (-> (path->js path) (string/replace #"_" "-")))

(defn get-name
  [path-or-file]
  (-> path-or-file io/file .getName))

(defn path [& parts]
  (.getPath (apply io/file parts)))

(defn find-relative-path [dirs filepath]
  (if-let [file (io/file filepath)]
    (let [parent (->> dirs
                      (map io/file)
                      (some (fn [x] (if (file/parent? x file) x))))]
      (if parent (.getPath (file/relative-to parent file))))))

(defn find-original-path [source-paths dirs filepath]
  (if-let [rel-path (find-relative-path dirs filepath)]
    (or (some (fn [source-path]
                (let [f (io/file source-path rel-path)]
                  (if (.exists f)
                    (.getPath f))))
              source-paths)
        rel-path)
    filepath))

;;
;; Object serialization
;;

(defn serialize-object
  "Serialize given Object to String using Object Streams and encode the bytes
  as Base64 string."
  [e]
  (with-open [bos (ByteArrayOutputStream.)
              out (ObjectOutputStream. bos)]
    (.writeObject out e)
    (.encodeToString (Base64/getEncoder) (.toByteArray bos))))

(defn deserialize-object
  "Deserialize given Base64 encoding string using Object Streams and return the
  Object."
  [ba]
  (with-open [bis (ByteArrayInputStream. (.decode (Base64/getDecoder) ba))
              in  (ObjectInputStream. bis)]
    (.readObject in)))

(defn merge-cause-ex-data
  "Merges ex-data from all exceptions in cause stack. First value for a key is
  used."
  [ex]
  (loop [ex ex
         data {}]
    (if-let [c (.getCause ex)]
      (recur c (merge (ex-data ex) data))
      (merge (ex-data ex) data))))

(defn last-cause [ex]
  (loop [ex ex]
    (if (.getCause ex)
      (recur (.getCause ex))
      ex)))

(defn last-cause-message [ex]
  (loop [ex ex
         last-msg (.getMessage ex)]
    (if-let [c (.getCause ex)]
      (recur c (or (.getMessage c) last-msg))
      last-msg)))
