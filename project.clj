(defproject collection-backend "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-ring "0.8.2"]]
  :ring {:handler collection-backend.handler/app}
  :profiles
  {:dev {:dependencies [[ring-mock "0.1.3"]]}}
  :dependencies [[org.clojure/clojure "1.5.0"]
                 [compojure "1.1.5"]
                 [digest "1.4.3"]
                 [ring-cors "0.1.0"]
                 [hiccup "1.0.2"]
                 [sandbar/sandbar "0.4.0-SNAPSHOT"]
                 [org.clojure/data.json "0.2.1"]
                 [com.datomic/datomic-free "0.8.3826"]])
