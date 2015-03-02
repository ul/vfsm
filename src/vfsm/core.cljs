(ns vfsm.core)

(defn- run-actions
  ([rtdb spec ctx event] (run-actions rtdb spec ctx event identity))
  ([rtdb spec ctx event f]
   {:pre [(#{:entry :exit :input} event)]}
   ((->> (get-in spec [(:state rtdb) event])
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
              (assoc :state next-state)
              (run-actions spec ctx :entry)
              (transit spec ctx)))
        rtdb))
    rtdb
    (get-in spec [(:state rtdb) :transitions])))

(defn execute [rtdb spec ctx]
  (-> rtdb (input spec ctx) (transit spec ctx)))

(defn execute! [rtdb spec ctx]
  (swap! rtdb #(execute (vary-meta % assoc :rtdb/source rtdb) spec ctx)))

(defn- uuid-str []
  (let [fs (fn [n] (apply str (repeatedly n (fn [] (.toString (rand-int 16) 16)))))
        g  (fn [] (.toString (bit-or 0x8 (bit-and 0x3 (rand-int 15))) 16))
        sb (.append (goog.string.StringBuffer.)
                    (fs 8) "-" (fs 4) "-4" (fs 3) "-" (g) (fs 3) "-" (fs 12))]
    (.toString sb)))

(defn start [rtdb spec ctx]
  {:pre [(satisfies? IWatchable rtdb) (satisfies? IDeref rtdb) (satisfies? ISwap rtdb)
         (map? spec)]}
  (let [id (uuid-str)]
    (swap! rtdb update-in [:state] #(or % :init))
    (let [f #(execute! rtdb spec ctx)]
      (add-watch rtdb id #(when-not (= %3 %4) (f)))
      (f))
    id))

(defn stop [rtdb id]
  (remove-watch rtdb id))