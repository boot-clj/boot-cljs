(ns demo.other)

(.appendChild (.-body js/document) (.createTextNode js/document "test passed"))
