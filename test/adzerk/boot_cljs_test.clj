(ns adzerk.boot-cljs-test
  (:require [clojure.test :refer :all]
            [adzerk.boot-cljs :refer :all]
            [adzerk.boot-cljs.util :refer :all]
            [boot.pod :as pod]))

(deftest util-tests
  (testing "assoc-or should short circuit"

    (let [state (atom 0)
          coll  (assoc-or {:foo 0} :foo (swap! state inc))]
      (is (= @state 0))
      (is (= coll {:foo 0})))

    (let [state (atom 0)
          coll  (assoc-or {:foo 0} :bar (swap! state inc))]
      (is (= @state 1))
      (is (= coll {:foo 0 :bar 1})))

    (let [state1 (atom 0)
          state2 (atom 0)
          coll   (assoc-or {:foo 0 :baz 0} :bar (swap! state1 inc) :baz (swap! state2 inc))]
      (is (= @state1 1))
      (is (= @state2 0))
      (is (= coll {:foo 0 :bar 1 :baz 0})))

    (let [state1 (atom 0)
          state2 (atom 0)
          coll   (assoc-or {:foo 0} :bar (swap! state1 inc) :baz (swap! state2 inc))]
      (is (= @state1 1))
      (is (= @state2 1))
      (is (= coll {:foo 0 :bar 1 :baz 1}))))

  )
