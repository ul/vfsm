(ns vfsm.core
  (:import [clojure.lang IDeref IRef IAtom]
           (java.util UUID)))

(defn- run-actions
  ([rtdb spec actions event] (run-actions rtdb spec actions event identity))
  ([rtdb spec actions event f]
   {:pre [(#{:entry :exit :input} event)]}
   ((->> (f (get-in spec [(:state rtdb) event]))
         (map actions) (reduce comp))
     rtdb)))

(defn- input [rtdb spec actions]
  (run-actions rtdb spec actions :input
               (partial keep (fn [[condition action]]
                               (when (condition rtdb) action)))))

(defn- transit [rtdb spec actions]
  (reduce
    (fn [rtdb [condition next-state]]
      (if (condition rtdb)
        (reduced
          (-> rtdb
              (run-actions spec actions :exit)
              (assoc :state next-state)
              (run-actions spec actions :entry)
              (transit spec actions)))
        rtdb))
    rtdb
    (get-in spec [(:state rtdb) :transitions])))

(defn- execute [rtdb spec actions]
  (-> rtdb (input spec actions) (transit spec actions)))

(defn- execute! [rtdb spec actions]
  (swap! rtdb #(execute (vary-meta % assoc :rtdb/source rtdb) spec actions)))

(defn start [rtdb spec actions]
  {:pre [(instance? IRef rtdb) (instance? IAtom rtdb) (instance? IDeref rtdb)
         (map? spec) (map? actions)]}
  (let [id (str (UUID/randomUUID))]
    (swap! rtdb update-in [:state] #(or % :init))
    (let [f #(execute! rtdb spec actions)]
      (add-watch rtdb id #(when-not (= %3 %4) (f)))
      (f))
    id))

(defn stop [rtdb id]
  (remove-watch rtdb id))