(ns vfsm.graphml
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [clj-xpath.core :refer [$x xml->doc $x:text?]]
            [instaparse.core :as insta]))

(def node-parser
  (insta/parser
    "<node> = state (<newline> spec)*
     <spec> = entry|exit|input
     entry = <'E:'> (<space> action)+
     exit =  <'X:'> (<space> action)+
     input = <'I:'> (<space> condition <'=>'> <space> action)+
     <action> = symbol
     (* may be should ban arbitrary functions as conditions *)
     <condition> = #'[^\\n\\r]+?(?==>)'
     (* digits at the beginning are allowed because
        all symbols are converted in keywords *)
     <namespace> = #'[\\p{IsAlphabetic}0-9_.?!*+-]+'
     <name> = #'[\\p{IsAlphabetic}0-9_?!*+-]+'
     <symbol> = (namespace '/')? name
     (* allow whitespace because state will be dasherized *)
     <wsnamespace> = #'[ \\p{IsAlphabetic}0-9_.?!*+-]+'
     <wsname> = #'[ \\p{IsAlphabetic}0-9_?!*+-]+'
     state = (wsnamespace '/')? wsname
     newline = #'\\s+'
     space = #'[, \\t]+'"))

(defn- sanitize [xs]
  (->> xs
       (remove #(str/blank? (:text %)))
       (map #(-> %
                 (select-keys [:attrs :text])
                 (update-in [:text] str/trim)))))

(defn- get-all-by-tag [doc tag]
  (->> doc ($x (str "//" tag)) sanitize))

(defn- parse-node [s]
  (let [[[_ state-name] & actions] (node-parser s)]
    [(->kebab-case-keyword state-name)
     (reduce (fn [m [t & a]]
              (update-in m [t]
                         (fnil into [])
                         (if (= :input t)
                           (map (fn [[c a]] [(read-string c) (keyword a)])
                                (partition 2 a))
                           (map keyword a))))
             {} actions)]))

(defn- process-nodes [doc]
  (reduce
    (fn [m {:keys [text attrs]}]
      (let [[state actions] (parse-node text)]
        (assoc m (:id attrs) [state actions])))
    {}
    (get-all-by-tag doc "node")))


(defn- process-edges [doc nodes]
  (reduce
    (fn [m {:keys [text attrs]}]
      (update-in m
                 [(get-in nodes [(:source attrs) 0])]
                 (fnil conj [])
                 [(read-string text) (get-in nodes [(:target attrs) 0])]))
    {}
    (get-all-by-tag doc "edge")))

(defn- make-spec [nodes edges]
  (reduce-kv
    (fn [m _ [state actions]]
      (assoc m state
               (if-let [t (get edges state)]
                 (assoc actions :transitions t)
                 actions)))
    {} nodes))

(defmacro compile-spec [spec-path]
  (let [f        (io/resource spec-path)
        doc      (xml->doc (slurp f))
        nodes    (process-nodes doc)
        edges    (process-edges doc nodes)]
    (make-spec nodes edges)))