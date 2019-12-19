(defproject clj-samplify "0.0.1"
  :description "A clojure re-implementation of Samplify"
  :url "https://github.com/qzdl/samplify"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [clj-spotify "0.1.9"]
                 [clj-http "3.9.1"]]
  :main ^:skip-aot clj-samplify.core
  :target-path "target/%s"
  :profiles {:dev[:project/dev]
             :test[:project/dev :profiles/test]
             :project/dev {:source-paths ["dev-resources"]
                           :dependencies [[org.clojure/tools.namespace "0.2.11"]
                                          [loudmoauth "0.1.3"]
                                          [ring "1.7.0"]]}
             :profiles/test {}})
