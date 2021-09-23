(ns notion-widgets.notion-api
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [clj-time.core :as time]
            [clj-time.format :as time-format]
            [clojure.pprint :as pp :refer [pprint]]
            [clojure.string :as str]))

(def NOTION_API_HEADERS
  {"Authorization" "secret_mt9XpnujzYB8JQZC9X4PyYw0wMpsAzDr8BTInyPszuD"
   "Content-Type" "application/json"
   "Notion-Version" "2021-05-13"})

(def NOTION_API_ROOT_URL
  "https://api.notion.com/v1/")

(def GITHUB_PROJECT_ROOT_URL
  "https://univalence.github.io/notion-widgets/")

(do :helpers

    (defn pp [& xs]
      (mapv pprint xs) (last xs))

    (defn- body->json [options]
      (if-let [body (:body options)]
        (assoc options :body (json/write-str body))
        options))

    (defn- body->edn [response]
      (if-let [body (:body response)]
        (assoc response :body (json/read-str body :key-fn keyword))
        response))

    (def time-format
      (:date-time time-format/formatters))

    (defn parse-date-time [s]
      (time-format/parse time-format s))

    (defn unparse-date-time [date-time]
      (time-format/unparse time-format date-time)))




(defn edn-body [response]
  (if (= 200 (:status response))
    (-> (:body response)
        (json/read-str :key-fn keyword))
    response))

(defn page-content [page-id & [opts]]
  (client/get (str NOTION_API_ROOT_URL "blocks/" page-id "/children")
              (merge {:headers NOTION_API_HEADERS
                      :cookie-policy :none
                      :content-type :json
                      :accept :json}
                     opts)))

(defn page-blocks [page-id]
  (-> (page-content page-id)
      edn-body
      :results))

(defn delete-blocks [blocks]
  (doseq [{:as block :keys [id]} blocks]
    (println "delete block: " block)
    (client/delete (str NOTION_API_ROOT_URL "blocks/" id)
                   {:headers NOTION_API_HEADERS
                    :cookie-policy :none
                    :content-type :json
                    :accept :json})))

(defn append-blocks [page-id blocks]
  (println "append blocks: " blocks)
  (client/patch (str NOTION_API_ROOT_URL "blocks/" page-id "/children")
                {:headers NOTION_API_HEADERS
                 :body (json/write-str {:children blocks})
                 :cookie-policy :none
                 :content-type :json
                 :accept :json}))

(defn insert-before [page-id block-id block]
  (let [blocks (page-blocks page-id)
        blocks-to-delete (drop-while (fn [block] (not (= (:id block) block-id)))
                                     blocks)]
    (delete-blocks blocks-to-delete)
    (append-blocks page-id (vec (cons block blocks-to-delete)))))

(defn replace-block
  "could be implemented using 'delete-block + 'insert-before to avoid repetition"
  [page-id block-id block]
  (let [blocks (page-blocks page-id)
        blocks-to-delete (drop-while (fn [block] (not (= (:id block) block-id)))
                                     blocks)]
    (delete-blocks blocks-to-delete)
    (append-blocks page-id (vec (cons block (next blocks-to-delete))))))

#_(defn delete-trailing-empty-blocks [page-id & [cursor]]
  (let [{:as content :keys [next_cursor]} (edn-body (page-content page-id))]
    (if next_cursor
      )))


(comment

  (edn-body
    (client/get (str NOTION_API_ROOT_URL "databases/" "aef435c9307d467dbd32dd3a4b9a894d")
                {:headers NOTION_API_HEADERS}))

  (def do-you (edn-body
                (client/get (str NOTION_API_ROOT_URL "pages/" "0b1b6a612f4745c294bb258ab96cdb41")
                            {:headers NOTION_API_HEADERS})))

  (keys do-you)

  )

(defn db-pages-by-last-update [db-id]
  (->> (client/post (str NOTION_API_ROOT_URL "databases/" db-id "/query")
                    {:headers NOTION_API_HEADERS
                     :body (json/write-str
                             {:sorts [{:timestamp "last_edited_time" :direction "descending"}]})
                     :content-type :json
                     :accept :json})
       edn-body
       :results))

#_(page-blocks (:id (first (db-pages-by-last-update "aef435c9307d467dbd32dd3a4b9a894d"))))




(defn api-call
  ([url]
   (api-call :get url))
  ([method url & [options]]
   (-> options
       (assoc :method method
              :url (str NOTION_API_ROOT_URL url)
              :cookie-policy :none
              ;; :decode-cookies false
              )
       (update :headers merge NOTION_API_HEADERS)
       body->json
       client/request
       body->edn)))

(defn last-changes
  "return a sequence of most recently modified page|database objects (82 max)"
  []
  (->> (api-call :post
                 "search"
                 {:body {:query ""
                         :sort {:direction "descending"
                                :timestamp "last_edited_time"}}})
       :body
       :results))

(do :brute-api-wrap_aborted
    ;; databases
    ;; -------------------------------------------------------------------------------

    (defn get-databases []
      (api-call "databases"))

    (defn get-database [id]
      (api-call (str "databases/" id)))

    (defn create-database
      [{:as body :keys [title parent properties]}]
      (api-call :post "databases" {:body body :debug true}))

    #_(create-database {:parent {:type "page_id"
                                 :page_id "7cafec8d0a954cacb5f062ed25dd5c33"}
                        :properties {} #_{:zoub {:type "title" :title {}}}
                        ;:title "hello"
                        })

    (defn update-database
      [id {:as body :keys [title properties]}]
      (api-call :patch (str "databases/" id) {:body body}))

    (defn query-database
      [id {:as body :keys [filter sorts]}]
      (api-call :post (str "databases/" id "/query") {:body body}))

    ;; pages
    ;; -------------------------------------------------------------------------------

    (defn get-page [id]
      (api-call (str "pages/" id)))

    (defn create-page
      [{:keys [parent properties title children icon cover]}]
      )

    (defn update-page [])

    (defn db
      ([] (api-call "databases"))
      ([id] (api-call (str "databases/" id)))
      ([id options] (api-call :get (str "databases/" id) options))
      ([id method options] (api-call method (str "databases/" id) options)))

    (db "aef435c9307d467dbd32dd3a4b9a894d"))

(defn minus-a-minute [date-time]
  (time/minus date-time
              (time/minutes 1)))

(comment :listen-version1

         (def DEFAULT_LISTEN_OPTS
           {:from (minus-a-minute (time/now))
            :interval 1000
            :on-change (fn [opts change]
                         (println "change:")
                         (println (unparse-date-time (:from opts)))
                         (pprint change))})

         (defn last-changes
           "return a sequence of most recently modified page|database objects (82 max)"
           []
           (->> (api-call :post
                          "search"
                          {:body {:query ""
                                  :sort {:direction "descending"
                                         :timestamp "last_edited_time"}}})
                :body
                :results))

         #_(pp (last-changes))

         (defn listen
           "call the api every given 'interval
            call 'on-change on newly modified pages or databases (with opts as first argument)
            take a look at DEFAULT_LISTEN_OPTS for a concrete exemple of valid opts"
           [opts]
           (let [{:as opts
                  :keys [from on-change interval]}
                 (merge DEFAULT_LISTEN_OPTS opts)]
             (Thread/sleep interval)
             (if-let [news (->> (last-changes)
                                (take-while (fn [{:keys [last_edited_time]}]
                                              (time/after? (parse-date-time last_edited_time) from)))
                                seq)]
               (do (mapv (partial on-change opts) news)
                   (recur (assoc opts :from (-> news first :last_edited_time parse-date-time))))
               (recur opts)))))

(defn listener_initial-state [opts]
  (merge {:last-check-time (time/now)
          :interval 1000
          :on-change (fn [opts change]
                       (println "change:")
                       (println (unparse-date-time (:last-check-time opts)))
                       (pprint change))
          :seen #{}}
         opts))

(defn listener_fresh-change?
  [{:as _state :keys [last-check-time seen]}
   {:as change :keys [last_edited_time]}]
  (and (time/after? (parse-date-time last_edited_time)
                    (minus-a-minute last-check-time))
       (not (seen change))))

(defn listener_filter-changes
  [state pages]
  (take-while (partial listener_fresh-change? state)
              pages))

(defn listener_block-changes
  [state page-changes]
  (mapcat (fn [change]
            (map (fn [block] (assoc block :parent change))
                 (filter (partial listener_fresh-change? state)
                         (page-blocks (:id change)))))
          page-changes))

(defn listener_state-step [state block-changes]
  (-> (assoc state :last-check-time (time/now))
      (update :seen into block-changes)))

(defn listen
  "call the api every given 'interval
   call 'on-change on newly modified blocks (with 'listener-state as first argument)
   take a look at 'listener_initial-state for a concrete exemple of valid opts"
  [opts]
  (letfn [(listen-loop [{:as state
                         :keys [on-change interval]}]
            (Thread/sleep interval)
            (let [page-changes (listener_filter-changes state (last-changes))
                  block-changes (listener_block-changes state page-changes)]
              (mapv (partial on-change state) block-changes)
              (recur (listener_state-step state block-changes))))]
    (listen-loop
      (listener_initial-state opts))))


(defn command-block [block]
  (and (= "paragraph" (:type block))
       (let [first-text-sub-block (get-in block [:paragraph :text 0])]
         (and (= (:type first-text-sub-block) "text")
              (let [sub-block-content (get-in first-text-sub-block [:text :content])]
                (when-let [[_ command] (re-matches #"^#(.*)\.$" sub-block-content)]
                  (str/split command #" ")))))))

(defn paragraph-block [text]
  {:paragraph
   {:text [{:text {:content text}}]}})

(def commands
  {:greet
   (fn [{:as block :keys [id parent]}]
     (replace-block (:id parent)
                    id
                    (paragraph-block "Hello you !")))

   :display-change
   (fn [{:as block :keys [id parent]}]
     (replace-block (:id parent)
                    id
                    (paragraph-block (with-out-str (pprint block)))))

   :widget
   (fn [{:as block :keys [id parent]}
        widget-type]
     (replace-block (:id parent)
                    id
                    {:embed {:url (str GITHUB_PROJECT_ROOT_URL
                                       "widgets/" widget-type
                                       "?pageId=" (:id parent)
                                       "&blockId=" id)}}))

   :page-id
   (fn [{:as block :keys [id parent]}]
     (replace-block (:id parent)
                    id
                    (paragraph-block (str "current page ID is: " (:id parent)))))})

(comment

  (page-blocks "7cafec8d-0a95-4cac-b5f0-62ed25dd5c33")

  (future (listen {:on-change (fn [state block]
                                (when-let [[verb & args] (command-block block)]
                                  (if-let [cmd (get commands (keyword verb))]
                                    (apply cmd block args))))})))













(comment :workona-export

         (reduce
           (fn [{:as state :keys [tree path]} line]
             (let [[_ indentation content] (re-matches #"^( *)(.*)$" line)
                   line-depth (/ (count indentation) 2)
                   current-depth (count path)]
               (case (compare line-depth current-depth)
                 -1 ()
                 0 (update state :tree update-in path conj content)
                 1 ()
                 )))

           {:path [] :tree []}
           (clojure.string/split-lines (slurp "/Users/pierrebaille/Desktop/workona-data-2021-09-22.txt")))


         (compare 1 0))