(defproject notion-widgets "1.0.0-SNAPSHOT"
  :description "Demo Clojure web app"
  :url "http://notion-widgets.herokuapp.com"
  :license {:name "Eclipse Public License v1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [compojure "1.6.1"]
                 [ring/ring-jetty-adapter "1.7.1"]
                 [environ "1.1.0"]
                 [ring/ring-defaults "0.3.2"]
                 [clj-http "3.12.3"]
                 [org.clojure/data.json "1.0.0"]
                 [hiccup "1.0.5"]
                 [ring-cors "0.1.13"]]
  :min-lein-version "2.0.0"
  :plugins [[environ/environ.lein "0.3.1"]]
  :hooks [environ.leiningen.hooks]
  :uberjar-name "notion-widgets-standalone.jar"
  :profiles {:production {:env {:production true}}})
