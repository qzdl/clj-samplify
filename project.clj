(defproject clj-samplify "0.1.0-SNAPSHOT"
  :description "A clojure re-implementation of Samplify"
  :url "https://github.com/qzdl/samplify"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [clj-spotify "0.1.9"]]
  :main ^:skip-aot clj-samplify.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
