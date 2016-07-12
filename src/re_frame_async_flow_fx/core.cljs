(ns re-frame-async-flow-fx.core
  (:require [re-frame.core]
            [clojure.set :as set]))


(defn make-flow-event-handler
  [{:keys [id db-path rules first-dispatch]}]
  (let [all-events  (apply set/union (map :events rules))       ;; all of the events refered to in the spec
        new-state (fn [db seen started]
                    (assoc-in db db-path {:seen-events seen :started-tasks started}))]

    ;; return an event handler which will manage the flow
    ;; This event handler will receive 3 kinds of events
    ;;   (dispatch [:id :steup])
    ;;   (dispatch [:id :done])
    ;;   (dispatch [:id [:a :forwarded :event :vector])
    ;;
    (fn flow-event-hander
      [{:keys [db]} event-v]
        (cond = event-v
              ;; Setup the flow coordinator:
              ;;  1. Initialise the state  (seen-events and started-tasks)
              ;;  2. dispatch the first event, to kick start
              ;;  3. arrange for the events to get forwarded to this handler
              :setup {:db (new-state db #{} #{})
                      :dispatch first-dispatch
                      :event-forwarder {:register id
                                        :events   all-events
                                        :to       id}}

              ;; Teardown the flow coordinator:
              ;;  1. remove this event handler
              ;;  2. remove state
              ;;  3. stop the events forwarder
              :done (do
                      (re-frame.core/clear-event-handler! id)
                      {:db (dissoc db db-path)
                       :event-forwarder {:unregister id}})

              ;;
              ;; An new event has been forwarded to this handler. What does it mean?
              ;;  1. work out if this new event means we need to dispatch another
              ;;  2. remember the new state
              (let [[event-id forwarded-event] event-v
                    _   (assert (= id event-id))
                    _   (assert (vector? forwarded-event))
                    forwarded-event-id     (first forwarded-event)
                    {:keys [seen-events started-tasks]} (get-in db db-path)
                    new-seen-events    (conj seen-events forwarded-event-id)
                    ready-tasks    (startable-tasks rules new-seen-events started-tasks)   <-  XXX definition of startable tasks
                    ready-task-ids (->> ready-tasks (map :dispatch) set)
                    new-started-tasks (set/union started-tasks ready-task-ids)]
                {:db (new-state db new-seen-events new-started-tasks)
                 :dispatch (map #(conj (:dispatch %) forwarded-event-id) ready-tasks)})))))   <-  this is crap XXX  take into account special marker`:done`

(defn register-fx!
  []
  (re-frame.core/def-fx
    :aync-flow
    (fn [{:as flow :keys [id]}]
      ;; XXX check that flow is valid.
      (re-frame.core/def-event-fx
        id                           ;; add debug middleware???  XXXX
        (make-flow-event-handler flow))
      (re-frame.core/dispatch [id :steup]))))


