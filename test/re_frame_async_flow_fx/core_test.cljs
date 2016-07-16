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


(deftest test-massage-rules
  (is (= (core/massage-rules :my-id [{:when :seen? :events :1 :dispatch [:2]}])
         (list {:id 0 :when core/all-events-seen? :events #{:1} :dispatch (list [:2])})))

  (is (= (core/massage-rules :my-id [{:when :seen-both? :events [:1 :2] :dispatch :halt}])
         (list {:id 0 :when core/all-events-seen? :events #{:1 :2} :dispatch (list [:my-id :halt])})))

  (is (= (core/massage-rules :my-id [{:when :any-events-seen? :events #{:1 :2} :dispatch (list [:2] :halt)}])
         (list {:id 0 :when core/any-events-seen? :events #{:1 :2} :dispatch (list [:2] [:my-id :halt])}))))

(deftest test-make-flow-handler-steup
  (let [flow   {:first-dispatch [:1]
                :rules [
                        {:when :seen? :events :1 :dispatch [:2]}
                        {:when :seen? :events :3 :dispatch :halt}]}
        handler   (core/make-flow-event-handler flow)]
    (is (= (handler {:db {}} :setup)
           {:db {}
            :dispatch [:1]
            :event-forwarder {:register core/default-id
                              :events #{:1 :3}
                              :dispatch-to [core/default-id]}}))))


#_(deftest test-make-flow-handler-forward
  (let [flow   {:first-dispatch [:1]
                :id             :test-id
                :db-path        [:p]
                :rules [
                        {:when :seen? :events :1 :dispatch [:2]}
                        {:when :seen? :events :3 :dispatch :halt}]}
        handler   (core/make-flow-event-handler flow)]
    (is (= (handler {:db {:p {:seen-events #{:33} :started-tasks #{}}}} [:1])
           {:db {}
            :deregister-event-handler core/default-id
            :event-forwarder {:unregister core/default-id}}))))

(deftest test-make-flow-handler-halt
  (let [flow   {:first-dispatch [:1]
                :rules [
                        {:when :seen? :events :1 :dispatch [:2]}
                        {:when :seen? :events :3 :dispatch :halt}]}
        handler   (core/make-flow-event-handler flow)]
    (is (= (handler {:db {}} :halt)
           {:db {}
            :deregister-event-handler core/default-id
            :event-forwarder {:unregister core/default-id}}))))
