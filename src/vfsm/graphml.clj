(ns vfsm.graphml
  (:refer-clojure :exclude [resolve])
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.pprint :as pp]
            [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [clj-xpath.core :refer [$x xml->doc $x:text?]]
            [instaparse.core :as insta]))

;;; from backtick

(def ^:dynamic *resolve*)

(def ^:dynamic ^:private *gensyms*)

(defn- resolve [sym]
  (let [ns (namespace sym)
        n (name sym)]
    (if (and (not ns) (= (last n) \#))
      (if-let [gs (@*gensyms* sym)]
        gs
        (let [gs (gensym (str (subs n 0 (dec (count n))) "__auto__"))]
          (swap! *gensyms* assoc sym gs)
          gs))
      (*resolve* sym))))

(defn unquote? [form]
  (and (seq? form) (= (first form) 'clojure.core/unquote)))

(defn unquote-splicing? [form]
  (and (seq? form) (= (first form) 'clojure.core/unquote-splicing)))

(defn- quote-fn* [form]
  (cond
    (symbol? form) `'~(resolve form)
    (unquote? form) (second form)
    (unquote-splicing? form) (throw (Exception. "splice not in list"))
    (record? form) `'~form
    (coll? form)
    (let [xs (if (map? form) (apply concat form) form)
          parts (for [x xs]
                  (if (unquote-splicing? x)
                    (second x)
                    [(quote-fn* x)]))
          cat (doall `(concat ~@parts))]
      (cond
        (vector? form) `(vec ~cat)
        (map? form) `(apply hash-map ~cat)
        (set? form) `(set ~cat)
        (seq? form) `(apply list ~cat)
        :else (throw (Exception. "Unknown collection type"))))
    :else `'~form))

(defn quote-fn [resolver form]
  (binding [*resolve* resolver
            *gensyms* (atom {})]
    (quote-fn* form)))

(defmacro defquote [name resolver]
  `(let [resolver# ~resolver]
     (defn ~(symbol (str name "-fn")) [form#]
       (quote-fn resolver# form#))
     (defmacro ~name [form#]
       (quote-fn resolver# form#))))

(defquote template identity)

;;; parser

(def node-parser
  (insta/parser
    "<node> = state (<newline> spec)*
     state = (wsnamespace '/')? wsname
     <spec> = entry|exit|input
     entry = <'E:'> (<space> action)+
     exit =  <'X:'> (<space> action)+
     input = <'I:'> (<space> condition <'=>'> <space> action)+
     <action> = symbol
     (* may be should ban arbitrary functions as conditions *)
     <condition> = #'[^\\n\\r]+?(?==>)'
     <namespace> = #'\\p{IsAlphabetic}[\\p{IsAlphabetic}0-9_.?!*+-]*'
     <name> = #'[\\p{IsAlphabetic}_?!*+-][\\p{IsAlphabetic}0-9_?!*+-]*'
     <symbol> = (namespace '/')? name
     (* allow whitespace because state will be dasherized,
        allow digits etc. as first char because state will be keywordized *)
     <wsnamespace> = #'[ \\p{IsAlphabetic}0-9_.?!*+-]+'
     <wsname> = #'[ \\p{IsAlphabetic}0-9_?!*+-]+'
     newline = #'\\s*[\\r\\n]+\\s*'
     space = #'[, \\t]+'"))

(def edge-parser
  (insta/parser
    "<edge> = number <space> condition | !number condition
     <number> = #'\\d+'
     <condition> = #'(?s).+'
     space = #'\\s+'"))

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
                           (map (fn [[c a]] [(read-string c) (symbol a)])
                                (partition 2 a))
                           (map symbol a))))
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
      (let [text (edge-parser text)]
        (update-in m
                   [(get-in nodes [(:source attrs) 0])]
                   (fnil conj [])
                   [(if (> (count text) 1) (Integer/parseInt (first text)) 0)
                    (read-string (last text))
                    (get-in nodes [(:target attrs) 0])])))
    {}
    (get-all-by-tag doc "edge")))

(defn- make-spec [nodes edges]
  (reduce-kv
    (fn [m _ [state actions]]
      (assoc m state
               (if-let [t (get edges state)]
                 (assoc actions :transitions
                   (->> t (sort-by first) (mapv #(subvec % 1))))
                 actions)))
    {} nodes))

;;; compiler

(defn compile-spec* [f]
  (let [doc      (xml->doc (slurp f))
        nodes    (process-nodes doc)
        edges    (process-edges doc nodes)]
    (make-spec nodes edges)))

(defmacro compile-spec [f]
  (compile-spec* f))

(defmacro compile-spec-from-resources [f]
  (compile-spec* (io/resource f)))

(defn pp [form] (pp/write form :dispatch pp/code-dispatch))

(defn forms-str [forms]
  (str/join "\n" (map #(binding [*print-meta* true] (with-out-str (pp %))) forms)))

(defn actions-stub [f]
  (->> f slurp xml->doc process-nodes
       (mapcat #(-> % second second vals)) flatten set
       (map (fn [f] (template (defn ~f [c d] d))))
       forms-str))