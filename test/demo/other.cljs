(ns demo.other)

(set! (-> js/window .-document .-body .-innerText) "test passed")
