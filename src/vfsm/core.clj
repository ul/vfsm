(ns vfsm.core
  (:require [clojure.core.async :as async])
  (:import [clojure.lang IDeref IRef IAtom PersistentHashMap]))

(defprotocol ISpec
  (entry-actions [_ state])
  (exit-actions  [_ state])
  (input-actions [_ state])
  (transitions   [_ state]))

(defprotocol IActions
  (run [_ ids]))

(defprotocol IAutomaton
  (start     [_])
  (stop      [_])
  (execute   [_ inputs])
  (get-id    [_])
  (get-state [_]))

(defn- ensure-coll [x]
  (when x (if (coll? x) x [x])))

(extend-type PersistentArrayMap
  ISpec
  (entry-actions [this state] (get-in this [state :entry]))
  (exit-actions  [this state] (get-in this [state :exit]))
  (input-actions [this state] (partition 2 (get-in this [state :input])))
  (transitions   [this state] (partition 2 (get-in this [state :transitions])))
  IActions
  (run [this ids]
    {:pre [(every? (partial contains? this) (ensure-coll ids))]}
    (doseq [id (ensure-coll ids)] ((get this id)))))

(defn- run-input-actions [spec actions state inputs]
  (doseq [[condition action-ids] (input-actions spec @state)]
    (when (condition inputs)
      (run actions action-ids))))

(defn- do-transitions [spec actions state inputs]
  (when-let
    [next-state
     (some->>
       (transitions spec @state)
       (drop-while (fn [[c _]] (not (c inputs))))
       first
       second)]
    (run actions (exit-actions spec @state))
    (reset! state next-state)
    (run actions (entry-actions spec @state))
    (do-transitions spec actions state inputs)))

(deftype Automaton [id spec actions state prev-inputs queue]
  IAutomaton
  (get-id [_] id)
  (get-state [_] @state)
  (start [this]
    (when (nil? queue)
      (let [queue (async/chan (async/sliding-buffer 1))]
        (set! (.queue this) queue)
        (async/go-loop []
          (when-let [inputs (async/<! queue)]
            (when-not (= @prev-inputs inputs)
              (reset! prev-inputs inputs)
              (run-input-actions spec actions state inputs)
              (do-transitions spec actions state inputs))
            (recur))))))
  (execute [this inputs]
    (if queue
      (do
        (async/put! queue inputs))
  (stop [this]
    (async/close! queue)
    (set! (.queue this) nil))
  IDeref
  (deref [this] (get-state this))
  Object
  (toString [this] (str "Automaton: " (get-id this) "@" (get-state this))))

(defmethod print-method Automaton
  [v w]
  (.write w (str v)))

(defn- uuid-str []
  (str (java.util.UUID/randomUUID)))

(defn automaton [spec actions & [init-state]]
  {:pre  [(satisfies? ISpec spec) (satisfies? IActions actions)]
   :post [(satisfies? IAutomaton %)]}
  (Automaton. (uuid-str) spec actions (atom (or init-state :init)) (atom nil) nil))

(defn bind-inputs [automaton inputs]
  {:pre [(satisfies? IAutomaton automaton)
         (satisfies? IRef       inputs)
         (satisfies? IDeref     inputs)]}
  (start automaton)
  (add-watch inputs (get-id automaton) #(execute automaton %4))
  (execute automaton @inputs))

(defn unbind-inputs [automaton inputs]
  {:pre [(satisfies? IAutomaton automaton) (satisfies? IRef inputs)]}
  (remove-watch inputs (get-id automaton)))