(ns re-frame-async-flow-fx.core-test
  (:require [cljs.test :refer-macros [is deftest]]
            [re-frame-async-flow-fx.core :as core]))

(deftest test-all-events-seen?
  (is (= (core/all-events-seen? #{:a}     #{:a})    true))
  (is (= (core/all-events-seen? #{:a}     #{:a :b}) true))
  (is (= (core/all-events-seen? #{:a :b}  #{:a :b}) true))
  (is (= (core/all-events-seen? #{:a}     #{:b})    false))
  (is (= (core/all-events-seen? #{:a :b}  #{:a :c}) false))
  (is (= (core/all-events-seen? #{:a}     #{:b :c}) false))
  (is (= (core/all-events-seen? #{:a}     #{})      false)))


(deftest test-any-events-seen?
  (is (= (core/any-events-seen? #{:a}     #{:a})    true))
  (is (= (core/any-events-seen? #{:a :b}  #{:a :b}) true))
  (is (= (core/any-events-seen? #{:a :b}  #{:a :c}) true))
  (is (= (core/any-events-seen? #{:a}     #{:b})    false))
  (is (= (core/any-events-seen? #{:a}     #{})      false)))


(deftest test-newly-startable-tasks
  (let [rules [{:id 1 :when core/all-events-seen?  :events #{:a :b}}
               {:id 2 :when core/all-events-seen?  :events #{:a}}]]
  (is (= (core/newly-startable-tasks rules #{:c}  #{})
         []))
  (is (= (core/newly-startable-tasks rules #{:a}  #{2})
         []))
  (is (= (core/newly-startable-tasks rules #{:a}  #{1})
         [(nth rules 1)]))
  (is (= (core/newly-startable-tasks rules #{:a :b} #{2})
         [(nth rules 0)]))
  (is (= (core/newly-startable-tasks rules #{:a}  #{})
         [(nth rules 1)]))))


