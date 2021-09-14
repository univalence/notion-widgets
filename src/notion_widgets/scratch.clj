(ns notion-widgets.scratch
  (:require [clj-http.client :as client]))

(client/get "http://localhost:5000")

(client/post (str "http://localhost:5000/create-widget")
             { ;:params {:widgetType "poi" :uri 12} ;; ;;
              :body "{'uri': 'foo', 'widgetType': 'one'}"
              :content-type :json
              ;; :accept :json
              })
