(ns vfsm.core
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require [cljs.core.async :as async]))

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
        (set! (.-queue this) queue)
        (go-loop []
          (when-let [inputs (async/<! queue)]
            (when-not (= @prev-inputs inputs)
              (reset! prev-inputs inputs)
              (run-input-actions spec actions state inputs)
              (do-transitions spec actions state inputs)
            (recur))))))
  (execute [this inputs]
    (if queue
      (async/put! queue inputs)
  (stop [this]
    (async/close! queue)
    (set! (.-queue this) nil))
  IDeref
  (-deref [this] (get-state this))
  Object
  (toString [this] (str "Automaton: " (get-id this) "@" (get-state this))))

(defn- uuid-str []
  (let [fs (fn [n] (apply str (repeatedly n (fn [] (.toString (rand-int 16) 16)))))
        g  (fn [] (.toString (bit-or 0x8 (bit-and 0x3 (rand-int 15))) 16))
        sb (.append (goog.string.StringBuffer.)
             (fs 8) "-" (fs 4) "-4" (fs 3) "-" (g) (fs 3) "-" (fs 12))]
       (.toString sb)))

(defn automaton [spec actions & [init-state]]
  {:pre  [(satisfies? ISpec spec) (satisfies? IActions actions)]
   :post [(satisfies? IAutomaton %)]}
  (Automaton. (uuid-str) spec actions (atom (or init-state :init)) (atom nil) nil))

(defn bind-inputs [automaton inputs]
  {:pre [(satisfies? IAutomaton automaton)
         (satisfies? IWatchable inputs)
         (satisfies? IDeref     inputs)]}
  (start automaton)
  (add-watch inputs (get-id automaton) #(execute automaton %4))
  (execute automaton @inputs))

(defn unbind-inputs [automaton inputs]
  {:pre [(satisfies? IAutomaton automaton) (satisfies? IWatchable inputs)]}
  (remove-watch inputs (get-id automaton)))