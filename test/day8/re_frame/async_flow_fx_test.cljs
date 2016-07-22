(ns day8.re-frame.async-flow-fx.async-flow-fx-test
  (:require [cljs.test :refer-macros [is deftest]]
            [day8.re-frame.async-flow-fx :as core]))

(deftest test-all-events-seen?
  (is (= (core/seen-all-of? #{:a} #{:a}) true))
  (is (= (core/seen-all-of? #{:a} #{:a :b}) true))
  (is (= (core/seen-all-of? #{:a :b} #{:a :b}) true))
  (is (= (core/seen-all-of? #{:a} #{:b}) false))
  (is (= (core/seen-all-of? #{:a :b} #{:a :c}) false))
  (is (= (core/seen-all-of? #{:a} #{:b :c}) false))
  (is (= (core/seen-all-of? #{:a} #{}) false)))


(deftest test-any-events-seen?
  (is (= (core/seen-any-of? #{:a} #{:a}) true))
  (is (= (core/seen-any-of? #{:a :b} #{:a :b}) true))
  (is (= (core/seen-any-of? #{:a :b} #{:a :c}) true))
  (is (= (core/seen-any-of? #{:a} #{:b}) false))
  (is (= (core/seen-any-of? #{:a} #{}) false)))


(deftest test-newly-startable-tasks
  (let [rules [{:id 1 :when core/seen-all-of?  :events #{:a :b}}
               {:id 2 :when core/seen-all-of?  :events #{:a}}]]
  (is (= (core/startable-rules rules #{:c} #{})
         []))
  (is (= (core/startable-rules rules #{:a} #{2})
         []))
  (is (= (core/startable-rules rules #{:a} #{1})
         [(nth rules 1)]))
  (is (= (core/startable-rules rules #{:a :b} #{2})
         [(nth rules 0)]))
  (is (= (core/startable-rules rules #{:a} #{})
         [(nth rules 1)]))))


(deftest test-massage-rules
  (is (= (core/massage-rules :my-id [{:when :seen? :events :1 :dispatch [:2]}])
         (list {:id 0 :when core/seen-all-of? :events #{:1} :dispatch (list [:2])})))

  (is (= (core/massage-rules :my-id [{:when :seen-both? :events [:1 :2] :halt? true}])
         (list {:id 0 :when core/seen-all-of? :events #{:1 :2} :dispatch (list [:my-id :halt-flow])})))

  (is (= (core/massage-rules :my-id [{:when :seen-any-of? :events #{:1 :2} :dispatch [:2] :halt? true}])
         (list {:id 0 :when core/seen-any-of? :events #{:1 :2} :dispatch (list [:2] [:my-id :halt-flow])}))))


(deftest test-steup
  (let [flow {:first-dispatch [:1]
              :rules [
                      {:when :seen? :events :1 :dispatch [:2]}
                      {:when :seen? :events :3 :halt? true}]}
        handler-fn   (core/make-flow-event-handler flow)]
    (is (= (handler-fn {:db {}} [:dummy-id :setup])
           {:db             {}
            :dispatch       [:1]
            :forward-events {:register     core/default-id
                              :events      #{:1 :3}
                              :dispatch-to [core/default-id]}}))))

(deftest test-forwarding
  (let [flow {:first-dispatch [:start]
              :id             :test-id
              :db-path        [:p]
              :rules [{:id 0 :when :seen? :events :1 :dispatch [:2]}
                      {:id 1 :when :seen? :events :3 :halt? true}
                      {:id 2 :when :seen-any-of? :events [:4 :5] :dispatch [:6]}]}
        handler-fn  (core/make-flow-event-handler flow)]

    ;; event :no should cause nothing to happen
    (is (= (handler-fn
             {:db {:p {:seen-events #{:33}
                       :rules-fired #{}}}}
             [:test-id [:no]])
           {:db {:p {:seen-events #{:33 :no}
                     :rules-fired #{}}}}))

    ;; new event should not cause a new dispatch because task is already started  (:id 0 is in ::rules-fired)
    (is (= (handler-fn
             {:db {:p {:seen-events #{:1}
                       :rules-fired #{0}}}}
             [:test-id [:1]])
           {:db {:p {:seen-events #{:1} :rules-fired #{0}}}}))

    ;; new event should cause a dispatch
    (is (= (handler-fn
             {:db {:p {:seen-events #{}
                       :rules-fired #{}}}}
             [:test-id [:1]])
           {:db {:p {:seen-events #{:1} :rules-fired #{0}}}
            :dispatch (list [:2])}))

    ;; new event should cause a dispatch
    (is (= (handler-fn
             {:db {:p {:seen-events #{:1}
                       :rules-fired #{0}}}}
             [:test-id [:3]])
           {:db {:p {:seen-events #{:1 :3} :rules-fired #{0 1}}}
            :dispatch (list [:test-id :halt-flow])}))

    ;; make sure :seen-any-of? works
    (is (= (handler-fn
             {:db {:p {:seen-events #{}
                       :rules-fired #{}}}}
             [:test-id [:4]])
           {:db {:p {:seen-events #{:4} :rules-fired #{2}}}
            :dispatch (list [:6])}))))


(deftest test-halt1
  (let [flow {:first-dispatch [:1]
              :rules []}
        handler-fn   (core/make-flow-event-handler flow)]
    (is (= (handler-fn {:db {}} [:dummy-id :halt-flow])
           { ;; :db {}
            :deregister-event-handler core/default-id
            :forward-events           {:unregister core/default-id}}))))


;; Aggh. I don't have dissoc-in available to make this work.
#_(deftest test-halt2
    (let [flow {:id  :blah
                :db-path [:p]
                :first-dispatch [:1]
                :rules []}
          handler-fn   (core/make-flow-event-handler flow)]
      (is (= (handler-fn {:db {:p {:seen-events #{:33} :rules-fired #{}}}} :halt-flow)
             {:db                       {}
              :deregister-event-handler :blah
              :forward-events           {:unregister :blah}}))))
