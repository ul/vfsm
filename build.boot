(set-env!
  ;; using the sonatype repo is sometimes useful when testing Clojurescript / core.async
  ;; versions that not yet propagated to Clojars
  ;; :repositories #(conj % '["sonatype" {:url "http://oss.sonatype.org/content/repositories/releases"}])
  :dependencies '[[org.clojure/core.async "0.1.346.0-17112a-alpha"]
                  [org.clojure/clojure    "1.7.0-alpha5" :scope "provided"]
                  [boot/core              "2.0.0-rc9"    :scope "provided"]
                  [adzerk/boot-cljs       "0.0-2760-0"   :scope "test"]
                  [adzerk/bootlaces       "0.1.10"       :scope "test"]])

(require '[adzerk.bootlaces :refer :all])

(def +version+ "0.1.0")

(bootlaces! +version+ :dev-dependencies "vfsm/boot/pod_deps.edn")

(task-options!
  pom  {:project     'vfsm
        :version     +version+
        :description "VFSM spec compiler and executor."
        :url         "https://github.com/ul/vfsm"
        :scm         {:url "https://github.com/ul/vfsm"}
        :license     {"Eclipse Public License" "http://www.eclipse.org/legal/epl-v10.html"}})

