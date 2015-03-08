(ns vfsm.core
  (:import [clojure.lang IDeref IRef IAtom]
           (java.util UUID)))

(defn- ensure-coll [x]
  (if (coll? x) x [x]))

(defn- state-path [ctx]
  (ensure-coll (get ctx :state-path :state)))

(defn- state [rtdb ctx]
  (get-in rtdb (state-path ctx)))

(defn- run-actions
  ([rtdb spec ctx event] (run-actions rtdb spec ctx event identity))
  ([rtdb spec ctx event f]
   {:pre [(#{:entry :exit :input} event)]}
   ((->> (get-in spec [(state rtdb ctx) event])
         f (map #(partial % ctx)) (reduce comp))
     rtdb)))

(defn- input [rtdb spec ctx]
  (run-actions rtdb spec ctx :input
               (partial keep (fn [[condition action]]
                               (when (condition rtdb) action)))))

(defn- transit [rtdb spec ctx]
  (reduce
    (fn [rtdb [condition next-state]]
      (if (condition rtdb)
        (reduced
          (-> rtdb
              (run-actions spec ctx :exit)
              (assoc-in (state-path ctx) next-state)
              (run-actions spec ctx :entry)
              (transit spec ctx)))
        rtdb))
    rtdb
    (get-in spec [(state rtdb ctx) :transitions])))

(defn- ensure-state [rtdb ctx]
  (update-in rtdb (state-path ctx) #(or % :init)))

(defn execute [rtdb spec ctx]
  (-> rtdb (ensure-state ctx) (input spec ctx) (transit spec ctx)))

(defn execute! [rtdb spec ctx]
  (swap! rtdb #(execute (vary-meta % assoc :rtdb/source rtdb) spec ctx)))

(defn start [rtdb spec ctx]
  {:pre [(instance? IRef rtdb) (instance? IAtom rtdb) (instance? IDeref rtdb)
         (map? spec)]}
  (let [id (str (UUID/randomUUID))]
    (let [f #(execute! rtdb spec ctx)]
      (add-watch rtdb id #(when-not (= %3 %4) (f)))
      (f))
    id))

(defn stop [rtdb id]
  (remove-watch rtdb id))