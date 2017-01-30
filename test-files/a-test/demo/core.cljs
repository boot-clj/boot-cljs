(ns demo.core)

(.appendChild (.-body js/document) (.createTextNode js/document "test passed"))
