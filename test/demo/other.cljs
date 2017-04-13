(ns demo.other
  (:require left-pad))

(let [result (str "test passed " (left-pad 42 5 0))]
  (.appendChild (.-body js/document) (.createTextNode js/document result)))
