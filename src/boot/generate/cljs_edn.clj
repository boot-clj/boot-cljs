(ns boot.generate.cljs-edn
  (:require [boot.generate.edn :as gen-edn]
            [boot.new.templates :as tmpl]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn generate
  "Generate a .cljs.edn file relative to the prefix."
  [prefix name & [require init-fns compiler-options]]
  (let [[fs-name the-sym] (str/split name #"/")
        path (tmpl/name-to-path ns-name)
        ext "cljs.edn"
        edn-file (io/file (str prefix "/" path "." ext))]
    (when-not (.exists edn-file)
      (gen-edn/generate prefix fs-name
        (str "\n{:require "  require
             "\n :init-fns " init-fns
             "\n :compiler-options " compiler-options "}\n")
        ext))))
