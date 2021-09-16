(ns notion-widgets.notion-api
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]))

(def NOTION_API_HEADERS {"Authorization" "secret_mt9XpnujzYB8JQZC9X4PyYw0wMpsAzDr8BTInyPszuD"
                         "Content-Type" "application/json"
                         "Notion-Version" "2021-05-13"})

(def NOTION_API_ROOT_URL "https://api.notion.com/v1/")

(defn page-content [page-id]
  (client/get (str NOTION_API_ROOT_URL "blocks/" page-id "/children")
              {:headers NOTION_API_HEADERS
               :content-type :json
               :accept :json}))

(defn page-blocks [page-id]
  (-> (client/get (str NOTION_API_ROOT_URL "blocks/" page-id "/children")
                  {:headers NOTION_API_HEADERS
                   :content-type :json
                   :accept :json})
      (get :body)
      (json/read-str :key-fn keyword)
      (get :results)))

(defn delete-blocks [blocks]
  (doseq [{:as block :keys [id]} blocks]
    (println "delete block: " block)
    (client/delete (str NOTION_API_ROOT_URL "blocks/" id)
                   {:headers NOTION_API_HEADERS
                    :content-type :json
                    :accept :json})))

(defn append-blocks [page-id blocks]
  (println "append blocks: " blocks)
  (client/patch (str NOTION_API_ROOT_URL "blocks/" page-id "/children")
                {:headers NOTION_API_HEADERS
                 :body (json/write-str {:children blocks})
                 :content-type :json
                 :accept :json}))

(defn replace-block [page-id block-id block]
  (let [blocks (page-blocks page-id)
        blocks-to-delete (drop-while (fn [block] (not (= (:id block) block-id)))
                                     blocks)]
    (delete-blocks blocks-to-delete)
    (append-blocks page-id (vec (cons block (next blocks-to-delete))))))

(defn insert-before [page-id block-id block]
  (let [blocks (page-blocks page-id)
        blocks-to-delete (drop-while (fn [block] (not (= (:id block) block-id)))
                                     blocks)]
    (delete-blocks blocks-to-delete)
    (append-blocks page-id (vec (cons block blocks-to-delete)))))