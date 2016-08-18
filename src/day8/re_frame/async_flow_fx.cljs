(ns day8.re-frame.async-flow-fx
  (:require
    [re-frame.core :as re-frame]
    [clojure.set :as set]
    [day8.re-frame.forward-events-fx]))


(defn seen-all-of?
  [required-events seen-events]
  (empty? (set/difference required-events seen-events)))


(defn seen-any-of?
  [required-events seen-events]
  (some? (seq (set/intersection seen-events required-events))))


(defn startable-rules
  "Given the accumulated set of seen events and the set of rules already started,
  return the list of rules which should now be started"
  [rules now-seen-events rules-fired]
  (->> (remove (comp rules-fired :id) rules)
       (filterv (fn [task] ((:when task) (:events task) now-seen-events)))))


(def map-when->fn {:seen?        seen-all-of?
									 :seen-both?   seen-all-of?
									 :seen-all-of? seen-all-of?
									 :seen-any-of? seen-any-of?})

(defn when->fn
	[when-kw]
	(if-let [when-fn (map-when->fn when-kw)]
		when-fn
		(re-frame/console :error  "async-flow: got bad value for :when - " when-kw)))

(defn massage-rules
  "Massage the supplied rules as follows:
    - replace `:when` keyword value with a function implementing the predicate
    - ensure that only `:dispatch` or `:dispatch-n` is provided
    - add a unique :id, if one not already present"
  [rules]
	(->> rules
			 (map-indexed (fn [index {:as rule :keys [id when events dispatch dispatch-n halt?]}]
											{:id         (or id index)
											 :halt?      (or halt? false)
											 :when       (when->fn when)
											 :events     (if (coll? events) (set events) (hash-set events))
											 :dispatch-n (cond
																		 dispatch-n (if dispatch
																									(re-frame/console :error "async-flow: rule can only specify one of :dispatch and :dispatch-n. Got both: " rule)
																									dispatch-n)
																		 dispatch   (list dispatch)
																		 :else      '())}))))


;; -- Event Handler

(defn make-flow-event-handler
  "Given a flow definitiion, returns an event handler which implements this definition"
  [{:keys [id db-path rules first-dispatch]}]
  (let [
        ;; Subject to db-path, state is either stored in app-db or in a local atom
        ;; Two pieces of state are maintained:
        ;;  - the set of seen events
        ;;  - the set of started tasks
        _           (assert (or (nil? db-path) (vector? db-path)) "aync-flow: db-path must be a vector")
        local-store (atom {})
        set-state   (if db-path
                      (fn [db seen started]
                        (assoc-in db db-path {:seen-events seen :rules-fired started}))
                      (fn [db seen started]
                        (reset! local-store {:seen-events seen :rules-fired started})
                        db))
        get-state   (if db-path
                      (fn [db] (get-in db db-path))
                      (fn [_] @local-store))

        rules       (massage-rules rules)]       ;; all of the events refered to in the rules

    ;; Return an event handler which will manage the flow.
    ;; This event handler will receive 3 kinds of events:
    ;;   (dispatch [:id :setup])
    ;;   (dispatch [:id :halt-flow])
    ;;   (dispatch [:id [:forwarded :event :vector]])
    ;;
    ;; This event handler returns a map of effects - it expects to be registered using
		;; reg-event-fx
    ;;
    (fn async-flow-event-hander
      [{:keys [db]} event-v]

      (condp = (second event-v)
        ;; Setup this flow coordinator:
        ;;   1. Establish initial state - :seen-events and ::rules-fired are made empty sets
        ;;   2. dispatch the first event, to kick start flow
        ;;   3. arrange for the events to be forwarded to this handler
        :setup {:db             (set-state db #{} #{})
                :dispatch       first-dispatch
                :forward-events {:register    id
                                 :events      (apply set/union (map :events rules))
                                 :dispatch-to [id]}}

        ;; Teardown this flow coordinator:
        ;;   1. remove this event handler
        ;;   2. remove any state stored in app-db
        ;;   3. deregister the events forwarder
        :halt-flow {;; :db (dissoc db db-path)  ;; Aggh. I need dissoc-in to make this work.
                    :forward-events           {:unregister id}
                    :deregister-event-handler id}

        ;; Here we are managing the flow.
        ;; A new event has been forwarded, so work out what should happen:
        ;;  1. does this new event mean we should dispatch another?
        ;;  2. remember this event has happened
        (let [[_ [forwarded-event-id & args]] event-v
              {:keys [seen-events rules-fired]} (get-state db)
              new-seen-events (conj seen-events forwarded-event-id)
              ready-rules     (startable-rules rules new-seen-events rules-fired)
							add-halt?       (some :halt? ready-rules)
              ready-rules-ids (->> ready-rules (map :id) set)
              new-rules-fired (set/union rules-fired ready-rules-ids)
							new-dispatches  (cond-> (mapcat :dispatch-n ready-rules)
																			add-halt? vec
																			add-halt? (conj [id :halt-flow]))]
          (merge
            {:db       (set-state db new-seen-events new-rules-fired)}
            (when (seq new-dispatches) {:dispatch-n new-dispatches})))))))


(defn- ensure-has-id
	"Ensure `flow` has an id.
	Return a vector of [id flow]"
	[flow]
	(if-let [id (:id flow)]
		[id flow]
		(let [new-id (keyword (str "async-flow/" (gensym "id-")))]
			[new-id (assoc flow :id new-id)])))


;; -- Effect handler


(defn flow->handler
	"Action the given flow effect"
  [flow]
	(let [[id flow']  (ensure-has-id flow)]
		(re-frame/reg-event-fx id (make-flow-event-handler flow'))   ;; register event handler
		(re-frame/dispatch [id :setup])))                            ;; kicks things off

(re-frame/reg-fx
  :async-flow
  flow->handler)


