(ns notion-widgets.web
  (:require [compojure.core :refer [defroutes GET PUT POST DELETE ANY]]
            [compojure.handler :refer [site]]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [ring.adapter.jetty :as jetty]
            [environ.core :refer [env]]

            [compojure.core :refer :all]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [clj-http.client :as client]
            [clojure.data.json :as json]
            [hiccup.page :refer [html5]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.x-headers :as xh]
            [ring.middleware.params :as wp]))

(def ROOT_URL "https://univalence.github.io/notion-widgets/")


(def index-page
  (html5
    [:head
     #_[:meta {:charset "utf-8"}]
     #_[:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     #_[:link {:href "" :rel "stylesheet"}]
     [:body
      [:div#app "Yop"]
      [:button#post-btn "press me"]
      [:script
       "var url = (window.location != window.parent.location)
                  ? document.referrer
                  : document.location.href;

        const button = document.getElementById('post-btn');

        button.addEventListener('click', async _ => {
          try { const response = await fetch('/append-block', {
                  method: 'get'});
                console.log('Completed!', response);
              } catch(err) {
                console.error(`Error: ${err}`);
              }});"]]]))

(defn html-response [content]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body content})

(defn edn-response [data]
  {:status 200
   :headers {"Content-Type" "application/edn"}
   :body (pr-str data)})

(defn append-block [id]
  (println "here")
  (-> (client/patch (str "https://api.notion.com/v1/blocks/" id "/children")
                    {:body "{\"children\": [{\"object\": \"block\",\"type\": \"heading_2\",\"heading_2\": {\"text\": [{ \"type\": \"text\", \"text\": { \"content\": \"Pouet\" } }]}}]}"
                     :headers {"Authorization" "secret_mt9XpnujzYB8JQZC9X4PyYw0wMpsAzDr8BTInyPszuD"
                               "Content-Type" "application/json"
                               "Notion-Version" "2021-05-13"}
                     :content-type :json
                     :accept :json})
      :body
      (json/read-str :key-fn keyword)))

(defn append-embed [{:keys [pageId widgetType]}]
  (client/patch (str "https://api.notion.com/v1/blocks/" pageId "/children")
                {:body (json/write-str {:children [{:type "embed" :embed {:url (str ROOT_URL widgetType "?pageId=" pageId)}}]})
                 #_(str "{\"children\": [{  \"type\": \"embed\", \"embed\": {\"url\": \"" ROOT_URL widgetType "?pageId=" pageId "\"  }}]}")
                 :headers {"Authorization" "secret_mt9XpnujzYB8JQZC9X4PyYw0wMpsAzDr8BTInyPszuD"
                           "Content-Type" "application/json"
                           "Notion-Version" "2021-05-13"}
                 :content-type :json
                 :accept :json}))


(defroutes app-routes
           (GET "/" [] (html-response index-page))
           (GET "/append-block/:id" [id] (edn-response (append-block id)))
           (POST "/create-widget" {body :body} (edn-response (append-embed (read-string (slurp body)))))
           (route/not-found "Not Found"))

(def app
  (-> app-routes
      (wrap-cors
        :access-control-allow-origin ["*"]
        :access-control-allow-methods [:get :put :post :delete])
      (wrap-defaults (assoc-in site-defaults [:security :anti-forgery] false))
      (wp/wrap-params)
      (xh/wrap-frame-options {:allow-from "*"})))




(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))]
    (jetty/run-jetty (site #'app) {:port port :join? false})))

;; For interactive development:
;; (.stop server)
;; (def server (-main))

