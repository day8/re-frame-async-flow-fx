(ns day8.re-frame.async-flow-fx.async-flow-fx-test
  (:require [cljs.test :refer-macros [is deftest]]
            [day8.re-frame.async-flow-fx :as core]))


(deftest test-all-events-seen?
  (is (= (core/seen-all-of? #{:a} #{[:a]}) true))
  (is (= (core/seen-all-of? #{:a} #{[:a] [:b]}) true))
  (is (= (core/seen-all-of? #{:a :b} #{[:a] [:b]}) true))
  (is (= (core/seen-all-of? #{:a} #{[:b]}) false))
  (is (= (core/seen-all-of? #{:a :b} #{[:a] [:c]}) false))
  (is (= (core/seen-all-of? #{:a} #{[:b] [:c]}) false))
  (is (= (core/seen-all-of? #{:a} #{}) false)))

(deftest test-all-events-seen-vec?
  (is (= (core/seen-all-of? #{[:a]} #{[:a]}) true))
  (is (= (core/seen-all-of? #{[:a]} #{[:a] [:b]}) true))
  (is (= (core/seen-all-of? #{[:a] [:b]} #{[:a] [:b]}) true))
  (is (= (core/seen-all-of? #{:a [:b]} #{[:a] [:b]}) true))
  (is (= (core/seen-all-of? #{[:a] [:b :c]} #{[:a] [:b]}) false))
  (is (= (core/seen-all-of? #{[:a]} #{[:b]}) false))
  (is (= (core/seen-all-of? #{[:a] [:b]} #{[:a] [:c]}) false))
  (is (= (core/seen-all-of? #{(fn [[e _]]
                                (keyword? e))} #{[:b] [:c]}) true))
  (is (= (core/seen-all-of? #{[:a]} #{}) false)))


(deftest test-any-events-seen?
  (is (= (core/seen-any-of? #{:a} #{[:a]}) true))
  (is (= (core/seen-any-of? #{:a [:b]} #{[:a] [:b]}) true))
  (is (= (core/seen-any-of? #{:a [:b]} #{[:a] [:c]}) true))
  (is (= (core/seen-any-of? #{:a} #{[:b]}) false))
  (is (= (core/seen-any-of? #{:a} #{}) false)))

(deftest test-any-events-seen-vec?
  (is (= (core/seen-any-of? #{[:a]} #{[:a]}) true))
  (is (= (core/seen-any-of? #{[:a] [:b]} #{[:a] [:b]}) true))
  (is (= (core/seen-any-of? #{[:a] [:b]} #{[:a] [:c]}) true))
  (is (= (core/seen-any-of? #{[:a]} #{[:b]}) false))
  (is (= (core/seen-any-of? #{[:a]} #{}) false)))

(deftest test-newly-startable-tasks
  (let [rules [{:id 1 :when core/seen-all-of?  :events #{:a :b}}
               {:id 2 :when core/seen-all-of?  :events #{:a}}]]
  (is (= (core/startable-rules rules #{[:c]} #{})
         []))
  (is (= (core/startable-rules rules #{[:a]} #{2})
         []))
  (is (= (core/startable-rules rules #{[:a]} #{1})
         [(nth rules 1)]))
  (is (= (core/startable-rules rules #{[:a] [:b]} #{2})
         [(nth rules 0)]))
  (is (= (core/startable-rules rules #{[:a]} #{})
         [(nth rules 1)]))))


(deftest test-massage-rules
  (is (= (core/massage-rules [{:when :seen? :events :1 :dispatch [:2]}])
         (list {:id 0 :when core/seen-all-of? :events #{:1} :halt? false :dispatch-n (list [:2])})))

  (is (= (core/massage-rules [{:when :seen-both? :events [:1 :2] :halt? true}])
         (list {:id 0 :when core/seen-all-of? :events #{:1 :2} :halt? true :dispatch-n '()})))

  (is (= (core/massage-rules [{:when :seen-any-of? :events #{:1 :2} :dispatch [:2] :halt? true}])
         (list {:id 0 :when core/seen-any-of? :events #{:1 :2} :halt? true :dispatch-n (list [:2])}))))


(deftest test-setup
  (let [flow       {:id             :some-id
                    :first-dispatch [:1]
                    :rules          [
                                     {:when :seen? :events :1 :dispatch [:2]}
                                     {:when :seen? :events :3 :halt? true}]}
        handler-fn (core/make-flow-event-handler flow)]
    (is (= (handler-fn {:db {}} [:dummy-id :setup])
           {:db             {}
            :dispatch       [:1]
            :forward-events {:register     :some-id
                              :events      #{:1 :3}
                              :dispatch-to [:some-id]}}))))

(deftest test-forwarding
  (let [flow {:first-dispatch [:start]
              :id             :test-id
              :db-path        [:p]
              :rules [{:id 0 :when :seen? :events :1 :dispatch [:2]}
                      {:id 1 :when :seen? :events :3 :halt? true}
                      {:id 2 :when :seen-any-of? :events [:4 :5] :dispatch [:6]}
                      {:id 3 :when :seen? :events :6 :halt? true :dispatch [:7]}
                      ]}
        handler-fn  (core/make-flow-event-handler flow)]

    ;; event :no should cause nothing to happen
    (is (= (handler-fn
             {:db {:p {:seen-events #{[:33]}
                       :rules-fired #{}}}}
             [:test-id [:no]])
          {:db {:p {:seen-events #{[:33] [:no]}
                    :rules-fired #{}}}}))

    ;; new event should not cause a new dispatch because task is already started  (:id 0 is in :rules-fired)
    (is (= (handler-fn
             {:db {:p {:seen-events #{[:1]}
                       :rules-fired #{0}}}}
             [:test-id [:1]])
          {:db {:p {:seen-events #{[:1]} :rules-fired #{0}}}}))

    ;; new event should cause a dispatch
    (is (= (handler-fn
             {:db {:p {:seen-events #{}
                       :rules-fired #{}}}}
             [:test-id [:1]])
          {:db         {:p {:seen-events #{[:1]} :rules-fired #{0}}}
           :dispatch-n [[:2]]}))

    ;; make sure :seen-any-of? works
    (is (= (handler-fn
             {:db {:p {:seen-events #{}
                       :rules-fired #{}}}}
             [:test-id [:4]])
          {:db         {:p {:seen-events #{[:4]} :rules-fired #{2}}}
           :dispatch-n [[:6]]}))))


(deftest test-vector-handling
  (let [flow        {:first-dispatch [:start]
                     :id             :test-id
                     :db-path        [:p]
                     :rules          [{:id 0 :when :seen? :events [[:1 :a]] :dispatch [:2]}
                                      {:id 2 :when :seen-any-of? :events [[:4 :b] :5] :dispatch [:6]}
                                      ]}
        handler-fn  (core/make-flow-event-handler flow)]

    ;; new event should cause a dispatch
    (is (= (handler-fn
             {:db {:p {:seen-events #{}
                       :rules-fired #{}}}}
             [:test-id [:1 :a]])
          {:db         {:p {:seen-events #{[:1 :a]} :rules-fired #{0}}}
           :dispatch-n [[:2]]}))

    ;; new event shouldn't cause a dispatch
    (is (= (handler-fn
             {:db {:p {:seen-events #{}
                       :rules-fired #{}}}}
             [:test-id [:1]])
          {:db         {:p {:seen-events #{[:1]} :rules-fired #{}}}}))

    ;; make sure :seen-any-of? works
    (is (= (handler-fn
             {:db {:p {:seen-events #{}
                       :rules-fired #{}}}}
             [:test-id [:4 :b]])
          {:db         {:p {:seen-events #{[:4 :b]} :rules-fired #{2}}}
           :dispatch-n [[:6]]}))))

(deftest test-halt1
  (let [flow {:first-dispatch [:start]
              :id             :test-id
              :db-path        [:p]
              :rules [{:id 1 :when :seen? :events :3 :halt? true}
                      {:id 3 :when :seen? :events :6 :halt? true :dispatch [:7]}
                      ]}
        handler-fn   (core/make-flow-event-handler flow)]
    ;; halt event should clean up
    (is (= (handler-fn
             {:db {:p {:seen-events #{[:1]}
                       :rules-fired #{0}}}}
             [:test-id [:3]])
           {:db         {}
            :forward-events {:unregister :test-id}
            :deregister-event-handler :test-id}))

    ;; halt event should clean up and dispatch
    (is (= (handler-fn
             {:db {:p {:seen-events #{[:1]}
                       :rules-fired #{0}}}}
             [:test-id [:6]])
           {:db                       {}
            :forward-events           {:unregister :test-id}
            :deregister-event-handler :test-id
            :dispatch-n               [[:7]]}))
    ))


(deftest test-halt2
    (let [flow {:id  :blah
                :db-path [:p]
                :first-dispatch [:1]
                :rules [{:when :seen? :events :3 :halt? true}]}
          handler-fn   (core/make-flow-event-handler flow)]
      (is (= (handler-fn {:db {:p {:seen-events #{[:33]} :rules-fired #{}}}}
                         [:blah [:3]])
             {:db                       {}
              :deregister-event-handler :blah
              :forward-events           {:unregister :blah}}))))

(deftest test-function-handling
  (let [flow {:first-dispatch [:start]
              :id             :test-id
              :db-path        [:p]
              :rules [{:id 0 :when :seen? :events (fn [[e _]] (= e :1)) :dispatch [:2]}
                      {:id 1 :when :seen? :events :3 :dispatch-fn (fn [[e data]] [[:4 data]])}]}
        handler-fn  (core/make-flow-event-handler flow)]

    ;; function in seen? should act as predicate
    (is (= (handler-fn
             {:db {:p {:seen-events #{}
                       :rules-fired #{}}}}
             [:test-id [:1]])
           {:db         {:p {:seen-events #{[:1]} :rules-fired #{0}}}
            :dispatch-n [[:2]]}))

    ;; function in dispatch should be called to provide events in dispatch-n
    (is (= (handler-fn
             {:db {:p {:seen-events #{}
                       :rules-fired #{}}}}
             [:test-id [:3 :some-data]])
           {:db                        {:p {:seen-events #{[:3 :some-data]} :rules-fired #{1}}}
            :dispatch-n                [[:4 :some-data]]}))))
