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
            [ring.middleware.params :as wp]

            [notion-widgets.notion-api :as api]))

(def GITHUB_PROJECT_ROOT_URL "https://univalence.github.io/notion-widgets/")

(defn html-response [content]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body content})

(defn json-response [content]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (json/write-str content)})

(defn edn-response [data]
  {:status 200
   :headers {"Content-Type" "application/edn"}
   :body (pr-str data)})

(defn find-form-block-id [page-id]
  (-> (api/page-content page-id)
      (get :body)
      (json/read-str :key-fn keyword)
      (get :results)
      (->> (filter (fn [{:keys [type embed]}]
                     (and (= type "embed")
                          (= "https://univalence.github.io/notion-widgets/" (:url embed))))))
      (first)
      (get :id)))

(defn replace-form-block
  [page-id widget-type form-block-id]
  (api/replace-block page-id form-block-id
                     {:embed {:url (str GITHUB_PROJECT_ROOT_URL "widgets/" widget-type "?pageId=" form-block-id)}})
  )


(defroutes app-routes
           (POST "/create-widget" {body :body}
             (let [{:keys [pageId widgetType]}
                   (json/read-str (slurp body) :key-fn keyword)]
               (replace-form-block pageId widgetType (find-form-block-id pageId))
               (json-response {:success true})))
           (route/not-found "Not Found"))

(def app
  (-> app-routes
      (wrap-defaults (assoc-in site-defaults [:security :anti-forgery] false))
      (wp/wrap-params)
      (wrap-cors
        :access-control-allow-origin [#".*"]
        :access-control-allow-methods [:get :put :post :delete])
      (xh/wrap-frame-options {:allow-from "*"})))




(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))]
    (jetty/run-jetty (site #'app) {:port port :join? false})))

;; For interactive development:
;; (.stop server)
;; (def server (-main))

