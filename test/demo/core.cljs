(ns demo.core)

(set! (-> js/window .-document .-body .-innerText) "test passed")
