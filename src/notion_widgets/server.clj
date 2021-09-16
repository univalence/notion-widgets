(ns notion-widgets.server
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]

            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.adapter.jetty :as jetty]
            [environ.core :refer [env]]

            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.x-headers :as xh]
            [ring.middleware.params :as wp]

            [notion-widgets.notion-api :as api]))

(def GITHUB_PROJECT_ROOT_URL
  "https://univalence.github.io/notion-widgets/")

(defn json-response [content]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (json/write-str content)})

(defn find-form-block-id [page-id]
  (-> (api/page-blocks page-id)
      (->> (filter (fn [{:keys [type embed]}]
                     (and (= type "embed")
                          (= GITHUB_PROJECT_ROOT_URL (:url embed))))))
      (first)
      (get :id)))

(defn replace-form-block
  [page-id widget-type form-block-id]
  (api/replace-block
    page-id form-block-id
    {:embed {:url (str GITHUB_PROJECT_ROOT_URL
                       "widgets/" widget-type
                       "?pageId=" form-block-id)}}))

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
      (wrap-cors :access-control-allow-origin [#".*"]
                 :access-control-allow-methods [:get :put :post :delete])
      (xh/wrap-frame-options {:allow-from "*"})))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))]
    (jetty/run-jetty #'app {:port port :join? false})))

;; For interactive development:
;; (.stop server)
;; (def server (-main))

