(ns vfsm.core)

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

(defn- uuid-str []
  (let [fs (fn [n] (apply str (repeatedly n (fn [] (.toString (rand-int 16) 16)))))
        g  (fn [] (.toString (bit-or 0x8 (bit-and 0x3 (rand-int 15))) 16))
        sb (.append (goog.string.StringBuffer.)
                    (fs 8) "-" (fs 4) "-4" (fs 3) "-" (g) (fs 3) "-" (fs 12))]
    (.toString sb)))

(defn start [rtdb spec actions]
  {:pre [(satisfies? IWatchable rtdb) (satisfies? IDeref rtdb) (satisfies? ISwap rtdb)
         (map? spec) (map? actions)]}
  (let [id (uuid-str)]
    (swap! rtdb update-in [:state] #(or % :init))
    (let [f #(execute! rtdb spec actions)]
      (add-watch rtdb id #(when-not (= %3 %4) (f)))
      (f))
    id))

(defn stop [rtdb id]
  (remove-watch rtdb id))