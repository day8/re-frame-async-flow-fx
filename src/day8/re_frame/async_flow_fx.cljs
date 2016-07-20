(ns day8.re-frame.async-flow-fx
  (:require [re-frame.core :as re-frame]
            [clojure.set :as set]))

(def default-id  :async/flow)

(defn seen-all-of?
  [required-events seen-events]
  (empty? (set/difference required-events seen-events)))


(defn seen-any-of?
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
    - replace `:when` keyword value with a function implementing the predicate
    - ensure that `:dispatch` is always a list (even of one item) and transform `:halt-flow` into an event.
    - turn :events into a set
    - add a unique :id, if one not already present"
  [flow-id rules]
  (let [halt-event  [flow-id :halt-flow]
        when->fn {:seen?        seen-all-of?
                  :seen-both?   seen-all-of?
                  :seen-all-of? seen-all-of?
                  :seen-any-of? seen-any-of?}]
    (->> rules
         (map-indexed (fn [index {:keys [id when events dispatch]}]
                        (let [when-as-fn  (when->fn when)
                              _  (assert (some? when-as-fn) (str "aync-flow: found bad value for :when: " when))]
                          {:id        (or id index)
                           :when      when-as-fn
                           :events    (if (coll? events) (set events) #{events})
                           :dispatch  (cond
                                        (vector? dispatch)  (list dispatch)
                                        (coll? dispatch)    (map (fn [d] (if (= d :halt-flow) halt-event d)) dispatch)
                                        (= :halt-flow dispatch)  (list halt-event)
                                        :else  (re-frame/console :error "aync-flow: dispatch value not valid: " dispatch))}))))))


;; -- Create Event Handler

(defn make-flow-event-handler
  "given a flow definitiion, returns an event handler which implements this definition"
  [{:keys [id db-path rules first-dispatch]}]
  (let [id  (or id default-id)

        ;; Subject to db-path, state is either stored in app-db or in a local atom
        ;; Two pieces of state are maintained:
        ;;  - the set of seen events
        ;;  - the set of started tasks
        _           (assert (or (nil? db-path) (vector? db-path)) "aync-flow: db-path must be a vector")
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

    ;; Return an event handler which will manage the flow.
    ;; This event handler will receive 3 kinds of events:
    ;;   (dispatch [:id :setup])
    ;;   (dispatch [:id :halt-flow])
    ;;   (dispatch [:id [:forwarded :event :vector]])
    ;;
    ;; This event handler returns a map of effects.
    ;;
    (fn async-flow-event-hander
      [{:keys [db]} event-v]

      (condp = (second event-v)
        ;; Setup this flow coordinator:
        ;;   1. Establish initial state - :seen-events and :started-tasks are made empty sets
        ;;   2. dispatch the first event, to kick start flow
        ;;   3. arrange for the events to be forwarded to this handler
        :setup {:db (set-state db #{} #{})
                :dispatch first-dispatch
                :event-forwarder {:register    id
                                  :events      (apply set/union (map :events rules))
                                  :dispatch-to [id]}}

        ;; Teardown this flow coordinator:
        ;;   1. remove this event handler
        ;;   2. remove any state stored in app-db
        ;;   3. deregister the events forwarder
        :halt-flow {;; :db (dissoc db db-path)  ;; Aggh. I need dissoc-in to make this work.
                    :event-forwarder {:unregister id}
                    :deregister-event-handler id}

        ;; Here we are managig the flow.
        ;; A new event has been forwarded to this handler. What does it mean?
        ;;  1. does this new event mean we need to dispatch another?
        ;;  2. remember this event has happened
        (let [[_ [forwarded-event-id & args]] event-v
              {:keys [seen-events started-tasks]} (get-state db)
              new-seen-events    (conj seen-events forwarded-event-id)
              ready-tasks        (newly-startable-tasks rules new-seen-events started-tasks)
              ready-task-ids     (->> ready-tasks (map :id) set)
              new-started-tasks  (set/union started-tasks ready-task-ids)]
          (merge
            {:db       (set-state db new-seen-events new-started-tasks)}
            (when (seq ready-tasks) {:dispatch (mapcat :dispatch ready-tasks)})))))))


;; -- Register effects handler with re-frame

(re-frame/def-fx
  :async-flow
  (fn [{:as flow :keys [id] :or {id default-id}}]
    (re-frame/def-event-fx
      id                                ;; add debug middleware if dp-path set ???  XXX
      (make-flow-event-handler flow))
    (re-frame/dispatch [id :setup])))


