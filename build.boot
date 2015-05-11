(def lein-proj 
  (->> 
    "project.clj" 
    slurp 
    read-string 
    (drop 3) 
    (partition 2) 
    (map vec) 
    (into {})))

(set-env!
  ;; using the sonatype repo is sometimes useful when testing Clojurescript / core.async
  ;; versions that not yet propagated to Clojars
  ;; :repositories #(conj % '["sonatype" {:url "http://oss.sonatype.org/content/repositories/releases"}])
  :dependencies (into [] (:dependencies lein-proj)))

(require '[adzerk.bootlaces :refer :all])

(def +version+ "0.1.0-SNAPSHOT")

(bootlaces! +version+)

(task-options!
  pom  {:project     'vfsm
        :version     +version+
        :description "VFSM spec compiler and executor."
        :url         "https://github.com/ul/vfsm"
        :scm         {:url "https://github.com/ul/vfsm"}
        :license     {"Eclipse Public License" "http://www.eclipse.org/legal/epl-v10.html"}})

