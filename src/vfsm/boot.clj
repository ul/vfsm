(ns vfsm.boot
  {:boot/export-tasks true}
  (:require [boot.core       :as boot]
            [boot.pod        :as pod]
            [boot.util       :as util]
            [clojure.java.io :as io]))

(def ^:private graphml-pod
  (delay (pod/make-pod (->> (-> "vfsm/boot/pod_deps.edn"
                                io/resource slurp read-string)
                            (update-in pod/env [:dependencies] into)))))

(boot/deftask graphml->clj
  "Compile GraphML VFSM spec to Clojure(Script).
  Naming must follow convention .cljs?.graphml,
  graph description must contain preamle with ns declaration."
  []
  (let [prev-fileset (atom nil)
        pod (future @graphml-pod)
        tmp (boot/temp-dir!)
        tmp-path (.getPath tmp)]
    (boot/with-pre-wrap fileset
      (let [graphml
            (->> fileset
                 (boot/fileset-diff @prev-fileset)
                 boot/input-files
                 (boot/by-ext [".graphml"])
                 (map (juxt boot/tmppath (comp (memfn getPath) boot/tmpfile))))]
        (doseq [[out-path in-path] graphml]
          (util/info "• %s\n" out-path)
          (pod/with-call-in @pod
            (vfsm.graphml/compile-spec ~in-path ~tmp-path))))
      (-> fileset (boot/add-resource tmp) boot/commit!))))