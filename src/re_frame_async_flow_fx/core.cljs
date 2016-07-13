(ns re-frame-async-flow-fx.core
  (:require [re-frame.core]
            [clojure.set :as set]))

(def default-id  :async/flow)

(defn all-events-seen?
  [required-events seen-events]
  (empty? (set/difference required-events seen-events)))


(defn any-events-seen?
  [required-events seen-events]
  (seq (set/intersection seen-events required-events)))

(def when->fn {:all-events-seen all-events-seen? :any-events-seen any-events-seen?})


(defn newly-startable-tasks
  "Given the accumulated set of seen events and the set of already started tasks,
  return the list of tasks which should now be started.
  In effect, give that the list of events seen has changed, what is the consequence."
  [rules now-seen-events started-tasks]
  (->> (remove (comp started-tasks :id) rules)
       (filterv (fn [task] ((:when task) (:events task) now-seen-events)))


(defn make-flow-event-handler
  [{:keys [id db-path rules first-dispatch]}]
  (let [id          (or id default-id)

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

        ;; Tweak supplied rules:
        ;; - replace `:when` keyword value with a function
        ;; - ensure that `:dispatch` is always a list and turn  `:done` into the right thing
        ;; - ensure that :events is a set
        rules   (-> rules
                    (map (fn [rule]
                           (update rule :when #(or (when->fn %1) %1))))
                    (map (fn [rule]
                           (update rule :dispatch (cond )))))

        all-events  (apply set/union (map :events rules))       ;; all of the events refered to in the spec
        ]

    ;; return an event handler which will manage the flow
    ;; This event handler will receive 3 kinds of events
    ;;   (dispatch [:id :setup])
    ;;   (dispatch [:id :done])
    ;;   (dispatch [:id [:a :forwarded :event :vector])
    ;;
    (fn flow-event-hander
      [{:keys [db]} event-v]
        (condp = event-v

              ;; Setup the flow coordinator:
              ;;  1. Initialise the state  (seen-events and started-tasks)
              ;;  2. dispatch the first event, to kick start
              ;;  3. arrange for the events to get forwarded to this handler
              :setup {:db (set-state db #{} #{})
                      :dispatch first-dispatch
                      :event-forwarder {:register id
                                        :events   all-events
                                        :to       id}}

              ;; Teardown the flow coordinator:
              ;;  1. remove this event handler
              ;;  2. remove state
              ;;  3. stop the events forwarder
              :done {:db (dissoc db db-path)
                     :event-forwarder {:unregister id}
                     :deregister-event-handler id})          ;; XXX write this effects handler

              ;; A new event has been forwarded to this handler. What does it mean?
              ;;  1. does this new event mean we need to dispatch another
              ;;  2. remember that this new event has happened
              (let [[_ [forwarded-event-id & args]] event-v
                    {:keys [seen-events started-tasks]} (get-state db)
                    new-seen-events    (conj seen-events forwarded-event-id)
                    ready-tasks        (newly-startable-tasks rules new-seen-events started-tasks)
                    ready-task-ids     (->> ready-tasks (map :dispatch) set)
                    new-started-tasks  (set/union started-tasks ready-task-ids)]
                {:db       (set-state db new-seen-events new-started-tasks)
                 :dispatch (concat (map :dispatch ready-tasks))})))))

(re-frame.core/def-fx
  :aync-flow
  (fn [{:as flow :keys [id]}]
    (re-frame.core/def-event-fx
      (or id default-id)                                 ;; add debug middleware???  XXXX
      (make-flow-event-handler flow))
    (re-frame.core/dispatch [id :steup])))


