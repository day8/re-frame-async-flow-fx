(ns day8.re-frame.async-flow-fx-test
  (:require [cljs.test :refer-macros [is deftest]]
            [re-frame.core :as rf]
            [day8.re-frame.test :as rf-test]
            [day8.re-frame.async-flow-fx :as core]))

;; Contributors: Be careful to use unique events and ids to avoid collisions
;; since this test environment could have more then one flow running.


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


(deftest test-sans-first-dispatch
  (rf-test/run-test-async
    (let [dispatched-events  (atom #{})
          note-event-handler (fn [_ event-v] (swap! dispatched-events conj event-v) {})
          flow               {:id    ::some-flow-id
                              :rules [{:when :seen? :events ::s1 :dispatch [::s2]}
                                      {:when :seen? :events ::s2 :dispatch [::s3]}
                                      {:when     :seen-all-of? :events [::s1 ::s2 ::s3]
                                       :dispatch [::flow-complete1] :halt? true}]}
          handler-fn         (core/make-flow-event-handler flow)]
      ;; Make flow handler should omit :dispatch when absent in flow
      (is (= (handler-fn {:db {}} [::dummy-id :setup])
             {:db             {}
              :forward-events {:register    ::some-flow-id
                               :events      #{::s1 ::s2 ::s3}
                               :dispatch-to [::some-flow-id]}}))
      ;; Register flow which does not have :first-dispatch and kick off manually
      (rf/reg-event-fx ::handler-with-flow-fx1 (fn [_ _] {:async-flow flow :dispatch [::s1]}))
      (rf/reg-event-fx ::s1 note-event-handler)
      (rf/reg-event-fx ::s2 note-event-handler)
      (rf/reg-event-fx ::s3 note-event-handler)
      (rf/reg-event-fx ::flow-complete1 note-event-handler)
      (rf/dispatch [::handler-with-flow-fx1])
      (rf-test/wait-for
        [::flow-complete1]
        (is (= @dispatched-events #{[::s1] [::s2] [::s3] [::flow-complete1]}))))))


(deftest test-timeout
  (rf-test/run-test-async
    (let [dispatched-events  (atom #{})
          state-on-timeout   (atom nil)
          note-event-handler (fn [_ event-v] (swap! dispatched-events conj event-v) {})
          flow               {:first-dispatch [::t1]
                              :timeout        [{:ms 1100 :dispatch [::timed-out1]}]
                              :rules          [{:when :seen? :events ::t1 :dispatch [::t2]}
                                               {:when :seen? :events ::t2 :dispatch [::t3]}
                                               {:when     :seen-all-of? :events [::t1 ::t2 ::t3 ::never-fires]
                                                :dispatch [::flow-complete2] :halt? true}
                                               {:when :seen? :events [[::halt-on-timeout1]] :halt? true}]}
          gen-flow           (core/make-flow-event-handler flow)]
      ;; Confirm flow includes :dispatch-n vec
      (is (-> (gen-flow {:db {}} [::dummy-id :setup]) (get :dispatch-later) vector))
      ;; Register flow and wait for timeout
      (rf/reg-event-fx ::handler-with-flow-fx2 (fn [_ _] {:async-flow flow}))
      (rf/reg-event-fx ::t1 note-event-handler)
      (rf/reg-event-fx ::t2 note-event-handler)
      (rf/reg-event-fx ::t3 note-event-handler)
      (rf/reg-event-fx ::never-fires note-event-handler)
      (rf/reg-event-fx ::flow-complete2 note-event-handler)
      (rf/reg-event-fx ::halt-on-timeout1 note-event-handler)
      (rf/reg-event-fx ::timed-out1
                       (fn [{:keys [db]} [event-kw state :as event-v]]
                         (reset! state-on-timeout (deref state))
                         (note-event-handler db event-v)))
      (rf/dispatch [::handler-with-flow-fx2])
      (rf-test/wait-for
        [::timed-out1]
        ; note1: ::never-fires and ::flow-complete never happened.
        ; note2: flow is not automatically torn down. It is up to implementor (rules) to
        ;        optionally finish flow on an timeout
        ; note3: If flow uses db-path, then the flow state is available there within the db
        ;        passed to the timeout event handler, otherwise a ref to the local flow state
        ;        is passed as last event arg, via a delay and can be dereferenced in the
        ;        timeout event handler to see if the flow completed or not.
        (is (= (->> @dispatched-events (map first) set) #{::t1 ::t2 ::t3 ::timed-out1}))
        (is (= #{0 1} (get @state-on-timeout :rules-fired)))
        (is (= #{::t1 ::t2 ::t3} (->> (get @state-on-timeout :seen-events) (map first) set)))))))


(deftest test-timeout-after-halt
  ;; Since timeout uses :dispatch-later, then the event (s) will always fire,
  ;; even when the flow may have already halted successfully or failed.
  ;; So that the handler can decide what to do at the time of the timeout,
  ;; it is handed a delay to the flow state (when db-path is not specified).
  ;; With this it can decide if the flow has already completed or not and in
  ;; turn choose if it wants to halt the flow or not.
  ;; If the handler chooses to halt, it should do so by dispatching one of
  ;; the existing halt rules.
  (rf-test/run-test-async
    (let [dispatched-events  (atom #{})
          state-on-timeout   (atom nil)
          note-event-handler (fn [_ event-v] (swap! dispatched-events conj event-v) {})
          flow               {:first-dispatch [::h1]
                              :timeout        [{:ms 1100 :dispatch [::timed-out2]}]
                              :rules          [{:when :seen? :events ::h1 :dispatch [::h2]}
                                               {:when :seen? :events ::h2 :dispatch [::h3]}
                                               {:when     :seen-all-of? :events [::h1 ::h2 ::h3]
                                                :dispatch [::flow-complete3] :halt? true}]}]
      ;; Register flow and wait for timeout
      (rf/reg-event-fx ::handler-with-flow-fx (fn [_ _] {:async-flow flow}))
      (rf/reg-event-fx ::h1 note-event-handler)
      (rf/reg-event-fx ::h2 note-event-handler)
      (rf/reg-event-fx ::h3 note-event-handler)
      (rf/reg-event-fx ::flow-complete3 note-event-handler)
      (rf/reg-event-fx ::timed-out2 (fn [{:keys [db]} [event-kw state :as event-v]]
                                     (reset! state-on-timeout (deref state))
                                     (note-event-handler db event-v)))
      (rf/dispatch [::handler-with-flow-fx])
      (rf-test/wait-for
        [::timed-out2]
        ; note: ::flow-complete3 occurs which is a halt rule. Unlike prior test-timeout case which never com.
        (is (= (->> @dispatched-events (map first) set) #{::h1 ::h2 ::h3 ::flow-complete3 ::timed-out2}))
        (is (= #{0 1 2} (get @state-on-timeout :rules-fired)))
        (is (= #{::h1 ::h2 ::h3} (->> (get @state-on-timeout :seen-events) (map first) set)))))))


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
