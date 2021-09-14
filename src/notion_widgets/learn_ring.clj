(ns notion-widgets.learn-ring
  (:require [clj-http.client :as client]))

;; handler : request -> response

(defn simple-handler [req]
  {:status 200 :req req})

(simple-handler {})
;; => {:status 200, :req {}}

;; middleware : handler -> handler

(defn simple-middleware [handler]
  (fn [req]
    (merge (handler req) {:foo :bar})))

(def simple-handler+
  (simple-middleware simple-handler))

(simple-handler+ {:tell-me :anything})
;; => {:status 200, :req {:tell-me :anything}, :foo :bar}

;; challenge: écrire un middleware qui merge la map donnée à toutes les réponses

(defn merging-middleware [m]
  (fn [handler]
    (fn [req] (merge m (handler req)))))


(def foobar-middleware
  (merging-middleware {:foo :bar}))

((foobar-middleware simple-handler) {:fiz :baz})

(assert (= ((foobar-middleware simple-handler) {:fiz :baz})
           {:foo :bar
            :status 200
            :req {:fiz :baz}}))


;; essai d'ajout via l'API d'un block embed

(client/patch (str "https://api.notion.com/v1/blocks/51904057270c4ba49ddd2805dcf3d2a0/children")
              {:body "{\"children\": [{  \"type\": \"embed\", \"embed\": {\"url\": \"https://pbaille.github.io/NOTION_WIDGET/\"  }}]}"
               :headers {"Authorization" "secret_mt9XpnujzYB8JQZC9X4PyYw0wMpsAzDr8BTInyPszuD"
                         "Content-Type" "application/json"
                         "Notion-Version" "2021-05-13"}
               :content-type :json
               :accept :json})


;;Step 1: user veut coller une url qui correspond a un widget
;;http://url.widget

;; le serveur reconnait que c'est une instantiation de widget
;; il sert un text input
;; l'utilisateur entre soit l'url complete de la page soit l'id de la page
;; le serveur demande le contenu de la page en question à L'API et remplace le embed block
;; en utilisant l'url donnée enrichie de l'id de la page hôte en query string

;;Step 2:

