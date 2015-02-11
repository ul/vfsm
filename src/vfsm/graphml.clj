(ns vfsm.graphml
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.string :as str]
            [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [boot.from.backtick :refer [template]]
            [clj-xpath.core :refer [$x xml->doc $x:text?]]))

(defn- sanitize [xs]
  (->> xs
       (remove #(str/blank? (:text %)))
       (map #(-> %
                 (select-keys [:attrs :text])
                 (update-in [:text] str/trim)))))

(defn- get-all-nodes [doc tag]
  (->> doc ($x (str "//" tag)) sanitize))

(defn- pp [form] (pp/write form :dispatch pp/code-dispatch))

(defn- forms-str [forms]
  (str/join "\n\n" (map #(with-out-str (pp %)) forms)))

(def ^:private prefix->action {"E:" :entry "X:" :exit "I:" :input})

(defn- parse-node [s]
  (let [[id & actions] (str/split s #"\n")]
    [(->kebab-case-keyword id)
     (reduce (fn [m a]
               (let [a (str/trim a)
                     prefix (subs a 0 2)
                     code (subs a 2 (count a))]
                 (if-let [action (get prefix->action prefix)]
                   (assoc m action (read-string code))
                   m)))
             {} actions)]))

(defn- write [f s]
  (when (and f s)
    (doto f io/make-parents (spit s))))

(defn- ns->path [ns ext]
  (-> ns (str/replace \. \/) (str "." ext)))

(defn- process-nodes [doc]
  (reduce
    (fn [m {:keys [text attrs]}]
      (let [[state actions] (parse-node text)]
        (assoc m (:id attrs) [state actions])))
    {}
    (get-all-nodes doc "node")))


(defn- process-edges [doc nodes]
  (reduce
    (fn [m {:keys [text attrs]}]
      (update-in m
                 [(get-in nodes [(:source attrs) 0])]
                 (fnil conj [])
                 (read-string text)
                 (get-in nodes [(:target attrs) 0])))
    {}
    (get-all-nodes doc "edge")))

(defn- process-preamble [doc]
  (read-string (str "(" ($x:text? "//graph/data" doc) ")")))

(defn- make-spec [nodes edges]
  (reduce-kv
    (fn [m _ [state actions]]
      (assoc m state
               (if-let [t (get edges state)]
                 (assoc actions :transitions t)
                 actions)))
    {} nodes))

(defn compile-spec [in-path out-path]
  (let [f        (io/file in-path)
        out-dir  (io/file out-path)
        ext      (-> (.getName f) (str/split #"\.") butlast last)
        doc      (xml->doc (slurp f))
        preamble (process-preamble doc)
        nodes    (process-nodes doc)
        edges    (process-edges doc nodes)
        spec     (make-spec nodes edges)
        spec     (template (~@preamble (def spec ~spec)))
        ns       (-> preamble first second str)]
    (write (io/file out-dir (ns->path ns ext)) (forms-str spec))))