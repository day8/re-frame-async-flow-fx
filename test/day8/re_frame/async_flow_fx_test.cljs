(ns day8.re-frame.async-flow-fx-test
  (:require [cljs.test :refer-macros [is deftest testing]]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [re-frame.registrar :as registrar]
            [day8.re-frame.test :as rf-test]
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
  (let [flow       {:id             :test-setup-flow
                    :debug?         true
                    :first-dispatch [:1]
                    :rules          [
                                     {:when :seen? :events :1 :dispatch [:2]}
                                     {:when :seen? :events :3 :halt? true}]}
        handler-fn (core/make-flow-event-handler flow)]
    (is (= (handler-fn {:db {}} [:dummy-id :setup])
           {:db             {}
            :dispatch       [:1]
            :forward-events {:register     :test-setup-flow
                              :events      #{:1 :3}
                              :dispatch-to [:test-setup-flow]}}))))


(deftest test-sans-first-dispatch
  (rf-test/run-test-async
    (let [dispatched-events  (atom #{})
          note-event-handler (fn [_ event-v] (swap! dispatched-events conj event-v) {})
          flow               {:id    :test-first-dispatch-flow
                              :rules [{:when :seen? :events ::1 :dispatch [::2]}
                                      {:when :seen? :events ::2 :dispatch [::3]}
                                      {:when     :seen-all-of? :events [::1 ::2 ::3]
                                       :dispatch [::flow-complete] :halt? true}]}
          handler-fn         (core/make-flow-event-handler flow)]
      ;; Make flow handler should omit :dispatch when absent in flow
      (is (= (handler-fn {:db {}} [:test-first-dispatch-flow :setup])
             {:db             {}
              :forward-events {:register    :test-first-dispatch-flow
                               :events      #{::1 ::2 ::3}
                               :dispatch-to [:test-first-dispatch-flow]}}))
      ;; Register flow which does not have :first-dispatch and kick off manually
      (rf/reg-event-fx ::handler-with-flow-fx (fn [_ _] {:async-flow flow :dispatch [::1]}))
      (rf/reg-event-fx ::1 note-event-handler)
      (rf/reg-event-fx ::2 note-event-handler)
      (rf/reg-event-fx ::3 note-event-handler)
      (rf/reg-event-fx ::flow-complete note-event-handler)
      (rf/dispatch [::handler-with-flow-fx])
      (rf-test/wait-for
        [::flow-complete]
        (is (= @dispatched-events #{[::1] [::2] [::3] [::flow-complete]}))))))


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
                     :id             :test-vector-id
                     :db-path        [:p]
                     :rules          [{:id 0 :when :seen? :events [[:1 :a]] :dispatch [:2]}
                                      {:id 2 :when :seen-any-of? :events [[:4 :b] :5] :dispatch [:6]}
                                      ]}
        handler-fn  (core/make-flow-event-handler flow)]

    ;; new event should cause a dispatch
    (is (= (handler-fn
             {:db {:p {:seen-events #{}
                       :rules-fired #{}}}}
             [:test-vector-id [:1 :a]])
          {:db         {:p {:seen-events #{[:1 :a]} :rules-fired #{0}}}
           :dispatch-n [[:2]]}))

    ;; new event shouldn't cause a dispatch
    (is (= (handler-fn
             {:db {:p {:seen-events #{}
                       :rules-fired #{}}}}
             [:test-vector-id [:1]])
          {:db         {:p {:seen-events #{[:1]} :rules-fired #{}}}}))

    ;; make sure :seen-any-of? works
    (is (= (handler-fn
             {:db {:p {:seen-events #{}
                       :rules-fired #{}}}}
             [:test-vector-id [:4 :b]])
          {:db         {:p {:seen-events #{[:4 :b]} :rules-fired #{2}}}
           :dispatch-n [[:6]]}))))


(deftest test-halt1
  (let [flow {:first-dispatch [:start]
              :id             :test-halt-id
              :db-path        [:p]
              :rules [{:id 1 :when :seen? :events :3 :halt? true}
                      {:id 3 :when :seen? :events :6 :halt? true :dispatch [:7]}
                      ]}
        handler-fn   (core/make-flow-event-handler flow)]
    ;; halt event should clean up
    (is (= (handler-fn
             {:db {:p {:seen-events #{[:1]}
                       :rules-fired #{0}}}}
             [:test-halt-id [:3]])
           {:db         {}
            :forward-events {:unregister :test-halt-id}
            :deregister-event-handler :test-halt-id}))

    ;; halt event should clean up and dispatch
    (is (= (handler-fn
             {:db {:p {:seen-events #{[:1]}
                       :rules-fired #{0}}}}
             [:test-halt-id [:6]])
           {:db                       {}
            :forward-events           {:unregister :test-halt-id}
            :deregister-event-handler :test-halt-id
            :dispatch-n               [[:7]]}))))


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


(deftest test-deregister-with-multiple-triggers-and-halt
  ;; Some events watched by a flow are fired more then once, however the flow only
  ;; cares about seeing at least one, then halt. This tests that is handled correctly, issue #33
  ;; We setup with the same event being fired 3 times. Once on first-dispatch, then the rule
  ;; fires it two more times. Each dispatch has a different arg value to help distinguish them.
  ;; What is expected:
  ;; - the flow should queue [::multi 1] on setup
  ;; - the rule should queue [::multi 2] and [::multi 3], stop and deregister.
  ;; - by the time we see the second ::multi, the flow should have unregistered all traces of itself.
  (rf-test/run-test-async
    (let [dispatched-events  (atom #{})
          note-event-handler (fn [{:keys [db]} event-v]
                               (swap! dispatched-events conj event-v)
                               {})
          flow               {:id             ::flow-with-multiple-triggers
                              :db-path        [:flow-state]
                              :first-dispatch [::multi 1]
                              :rules          [{:id         :only-rule
                                                :when       :seen? :events [::multi]
                                                :dispatch-n [[::multi 2] [::multi 3]] :halt? true}]}
          handler-fn         (core/make-flow-event-handler flow)
          post-setup         (handler-fn {:db {}} [::flow-with-multiple-triggers :setup])]
      (is (= post-setup
             {:db             {:flow-state {:seen-events #{} :rules-fired #{}}}
              :forward-events {:register    ::flow-with-multiple-triggers
                               :events      #{::multi}
                               :dispatch-to [::flow-with-multiple-triggers]}
              :dispatch       [::multi 1]}))
      ;; Register our events and kick off flow with fx.
      (rf/reg-event-fx ::handler-with-flow-fx (fn [_ _] {:async-flow flow}))
      (rf/reg-event-fx ::multi note-event-handler)
      (rf/dispatch [::handler-with-flow-fx])
      (rf-test/wait-for
        [::multi]
        ; This is the :first-dispatch of ::multi triggered by :setup
        ; At this point the flow and forwarded-events should be registered.
        (is (= @dispatched-events #{[::multi 1]}))
        (is (= {:seen-events #{} :rules-fired #{}}) (get @app-db :flow-state))
        (is (registrar/get-handler :event ::flow-with-multiple-triggers true))
        (rf-test/wait-for
          [::multi]
          ; This is the second dispatch of ::multi triggered by the rule's dispatch-n.
          ; At this point the rule is considered fired (the third dispatch is still queued),
          ; but due to :halt? true, the flow and forwarders should also no longer be registered.
          (is (= @dispatched-events #{[::multi 1] [::multi 2]}))
          (is (nil? (get @app-db :flow-state)) "We should have no flow state at this point")
          (is (nil? (registrar/get-handler :event ::flow-with-multiple-triggers false)))
          (rf-test/wait-for
            [::multi]
            ; This is the third and final final dispatch of ::multi from the flow rule.
            ; This is purely proving that queued events continue to flow in re-frame after the flow is gone.
            (is (= @dispatched-events #{[::multi 1] [::multi 2] [::multi 3]}))
            (is (nil? (get @app-db :flow-state)) "We should have no flow state at this point")
            (is (nil? (registrar/get-handler :event ::flow-with-multiple-triggers false)))))))))


(deftest test-function-handling
  (let [flow {:first-dispatch [:start]
              :id             :test-fn-id
              :db-path        [:p]
              :rules [{:id 0 :when :seen? :events (fn [[e _]] (= e :1)) :dispatch [:2]}
                      {:id 1 :when :seen? :events :3 :dispatch-fn (fn [[e data]] [[:4 data]])}]}
        handler-fn  (core/make-flow-event-handler flow)]

    ;; function in seen? should act as predicate
    (is (= (handler-fn
             {:db {:p {:seen-events #{}
                       :rules-fired #{}}}}
             [:test-fn-id [:1]])
           {:db         {:p {:seen-events #{[:1]} :rules-fired #{0}}}
            :dispatch-n [[:2]]}))

    ;; function in dispatch should be called to provide events in dispatch-n
    (is (= (handler-fn
             {:db {:p {:seen-events #{}
                       :rules-fired #{}}}}
             [:test-fn-id [:3 :some-data]])
           {:db                        {:p {:seen-events #{[:3 :some-data]} :rules-fired #{1}}}
            :dispatch-n                [[:4 :some-data]]}))))

(deftest test-notify-handling
  ;; This flow test does not have a halt? condition so causes a warning.
  (let [flow       {:first-dispatch [:start]
                    :id             :test-notify-id
                    :db-path        [:p]
                    :rules          [{:id 0 :when :seen? :events [[::core/notify :test-notify]] :dispatch [:1]}]}
        handler-fn (core/make-flow-event-handler flow)]
    ;; function in seen? should act as predicate
    (is (= (handler-fn
             {:db {:p {:seen-events #{}
                       :rules-fired #{}}}}
             [:test-notify-id [::core/notify :test-notify]])
           {:db         {:p {:seen-events #{[::core/notify :test-notify]} :rules-fired #{0}}}
            :dispatch-n [[:1]]}))))
