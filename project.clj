(defproject file-archiver "0.1.0-SNAPSHOT"
  :description "Copy files to a directory structure based on the SHA-224 checksum of the files."
  :url "https://github.com/Peter-Donner/sha224rch"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[digest "1.4.4"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [robert/hooke "1.3.0"]]
  :main ^:skip-aot sha224rch.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
