(ns re-frame-async-flow-fx.core-test
  (:require [cljs.test :refer-macros [is deftest]]
            [re-frame-async-flow-fx.core :as core]))

(deftest stub
  "stub test to excercise test framework and devtools"
  (let [data {:feed {:flint 5}}]
    (js-debugger))
  (is true))
