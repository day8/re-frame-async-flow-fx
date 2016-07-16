(ns re-frame-async-flow-fx.core
  (:require [re-frame.core]
            [clojure.set :as set]))

(def default-id  :async/flow)

(defn all-events-seen?
  [required-events seen-events]
  (empty? (set/difference required-events seen-events)))


(defn any-events-seen?
  [required-events seen-events]
  (some? (seq (set/intersection seen-events required-events))))


(defn newly-startable-tasks
  "Given the accumulated set of seen events and the set of tasks already started,
  return the list of tasks which should now be started"
  [rules now-seen-events already-started-tasks]
  (->> (remove (comp already-started-tasks :id) rules)
       (filterv (fn [task] ((:when task) (:events task) now-seen-events)))))


(defn massage-rules
  "Massage the supplied rules as follows:
    - replace `:when` keyword value with a function  implements the predicate
    - ensure that `:dispatch` is always a list and turn  `:done` into the right event
    - ensure that :events is a set
    - add a unique id"
  [id rules]
  (let [halt-event  [id :halt]
        when->fn {:seen? all-events-seen?
                  :seen-both? all-events-seen?
                  :all-events-seen? all-events-seen?
                  :any-events-seen? any-events-seen?}]
    (->> rules
         (map-indexed (fn [index {:keys [when events dispatch]}]
                        (let [w  (when->fn when)
                              _  (assert (some? w) (str "aync-flow: found illegal value for :when: " when))]
                          {:id        index
                           :when      w
                           :events    (if (coll? events) (set events) #{events})
                           :dispatch  (cond
                                        (vector? dispatch)  (list dispatch)
                                        (coll? dispatch)    (map (fn [d] (if (= d :halt) halt-event d)) dispatch)
                                        (= :halt dispatch)  (list halt-event)
                                        :else  (js/console.error "aync-flow: dispatch value not valid: " dispatch))}))))))


(defn make-flow-event-handler
  [{:keys [id db-path rules first-dispatch]}]
  (let [id  (or id default-id)

        ;; Subject to db-path, state is either stored in app-db or in a local atom
        ;; Two pieces of state are maintained:
        ;;  - the set of seen events
        ;;  - the set of started tasks
        local-store (atom {})
        set-state   (if db-path
                      (fn [db seen started]
                        (assoc-in db db-path {:seen-events seen :started-tasks started}))
                      (fn [db seen started]
                        (reset! local-store {:seen-events seen :started-tasks started})
                        db))
        get-state   (if db-path
                      (fn [db] (get-in db db-path))
                      (fn [_]  @local-store))

        rules  (massage-rules id rules)]       ;; all of the events refered to in the rules

    ;; return an event handler which will manage the flow
    ;; This event handler will receive 3 kinds of events
    ;;   (dispatch [:id :setup])
    ;;   (dispatch [:id :done])
    ;;   (dispatch [:id [:a :forwarded :event :vector])
    ;;
    (fn async-flow-event-hander
      [{:keys [db]} event-v]
        (condp = event-v

              ;; Setup the flow coordinator:
              ;;   1. Initialise the state  (seen-events and started-tasks)
              ;;   2. dispatch the first event, to kick start
              ;;   3. arrange for the events to get forwarded to this handler
              :setup {:db (set-state db #{} #{})
                      :dispatch first-dispatch
                      :event-forwarder {:register    id
                                        :events      (apply set/union (map :events rules))
                                        :dispatch-to [id]}}

              ;; Teardown the flow coordinator:
              ;;   1. remove this event handler
              ;;   2. remove any state stored in app-db
              ;;   3. stop the events forwarder
              :halt {:db (dissoc db db-path)
                     :event-forwarder {:unregister id}
                     :deregister-event-handler id}

              ;; A new event has been forwarded to this handler. What does it mean?
              ;;  1. does this new event mean we need to dispatch another
              ;;  2. save that this event has happened
              (let [[_ [forwarded-event-id & args]] event-v
                    {:keys [seen-events started-tasks]} (get-state db)
                    new-seen-events    (conj seen-events forwarded-event-id)
                    ready-tasks        (newly-startable-tasks rules new-seen-events started-tasks)
                    ready-task-ids     (->> ready-tasks (map :id) set)
                    new-started-tasks  (set/union started-tasks ready-task-ids)]
                {:db       (set-state db new-seen-events new-started-tasks)
                 :dispatch (concat (map :dispatch ready-tasks))})))))


;; -- Register handler with re-frame

(re-frame.core/def-fx
  :aync-flow
  (fn [{:as flow :keys [id]}]
    (re-frame.core/def-event-fx
      (or id default-id)                                 ;; add debug middleware if dp-patth set ???  XXX
      (make-flow-event-handler flow))
    (re-frame.core/dispatch [id :steup])))


