(defproject vfsm "0.1.0-SNAPSHOT"
  :description "VFSM spec compiler and executor."
  :url "https://github.com/ul/vfsm"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure             "1.7.0-beta3" :scope "provided"]
                 [com.github.kyleburton/clj-xpath "1.4.4"]
                 [camel-snake-kebab               "0.3.1"]
                 [instaparse                      "1.4.0"]
                 [adzerk/boot-cljs                "0.0-3269-0"  :scope "test"]
                 [adzerk/bootlaces                "0.1.11"      :scope "test"]])
