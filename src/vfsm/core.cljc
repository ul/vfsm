(ns vfsm.core
  #?(:clj (:import [clojure.lang IDeref IRef IAtom]
                   (java.util UUID))))

;;; Utils

(defn- uuid-str []
  #?(:clj
     (str (UUID/randomUUID))
     :cljs
     (let [fs (fn [n] (apply str (repeatedly n (fn [] (.toString (rand-int 16) 16)))))
           g (fn [] (.toString (bit-or 0x8 (bit-and 0x3 (rand-int 15))) 16))
           sb (.append (goog.string.StringBuffer.)
                       (fs 8) "-" (fs 4) "-4" (fs 3) "-" (g) (fs 3) "-" (fs 12))]
       (.toString sb))))

(defn- ensure-coll [x]
  (if (coll? x) x [x]))

(defn- state-path [ctx]
  (ensure-coll (get ctx :state-path :state)))

(defn- state [rtdb ctx]
  (get-in rtdb (state-path ctx)))

;;; Pure FSM mechanics

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

;;; Watch & update atom-like rtdb

(defn execute! [rtdb spec ctx]
  (swap! rtdb #(execute (vary-meta % assoc :rtdb/source rtdb) spec ctx)))

(defn start! [rtdb spec ctx]
  {:pre [#?(:clj  (instance? IRef rtdb)
                  (instance? IAtom rtdb)
                  (instance? IDeref rtdb)
            :cljs (satisfies? IWatchable rtdb)
                  (satisfies? IDeref rtdb)
                  (satisfies? ISwap rtdb))
         (map? spec)]}
  (let [id (uuid-str)]
    (let [f #(execute! rtdb spec ctx)]
      (add-watch rtdb id #(when-not (= %3 %4) (f)))
      (f))
    id))

(defn stop! [rtdb id]
  (remove-watch rtdb id))