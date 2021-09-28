(ns notion-widgets.notion-api
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [clj-time.core :as time]
            [clj-time.format :as time-format]
            [clojure.pprint :as pp :refer [pprint]]
            [clojure.string :as str]))

(do :constants

    (def NOTION_API_HEADERS
      {"Authorization" "secret_mt9XpnujzYB8JQZC9X4PyYw0wMpsAzDr8BTInyPszuD"
       "Content-Type" "application/json"
       "Notion-Version" "2021-05-13"})

    (def NOTION_API_ROOT_URL
      "https://api.notion.com/v1/")

    (def GITHUB_PROJECT_ROOT_URL
      "https://univalence.github.io/notion-widgets/")

    (def DEFAULT_HTTP_OPTS
      {:cookie-policy :none}))

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
      (time-format/unparse time-format date-time))

    (defn minus-a-minute [date-time]
      (time/minus date-time
                  (time/minutes 1)))

    ;; maybe useless
    (defn edn-body [response]
      (if (= 200 (:status response))
        (-> (:body response)
            (json/read-str :key-fn keyword))
        response)))

(do :blocks

    (defn paragraph-block
      [text]
      {:paragraph
       {:text [{:text {:content text}}]}})

    (defn empty-block?
      [block]
      (= (:paragraph block) {:text []})))

(do :api-calls

    (defn api-call
      ([url]
       (api-call :get url))
      ([method url & [options]]
       (-> options
           (assoc :method method
                  :url (str NOTION_API_ROOT_URL url))
           (merge DEFAULT_HTTP_OPTS)
           (update :headers merge NOTION_API_HEADERS)
           body->json
           client/request
           body->edn)))

    (defn last-changes
      "return a sequence of most recently modified page|database objects (82 max)"
      []
      (-> (api-call :post
                    "search"
                    {:body {:query ""
                            :sort {:direction "descending"
                                   :timestamp "last_edited_time"}}})
          (get-in [:body :results])))

    (defn page-content
      [page-id & [opts]]
      (api-call :get
                (str "blocks/" page-id "/children")
                opts))

    (defn page-blocks
      "return all blocks from a page,
       takes care of pagination"
      [page-id & [cursor]]
      (let [url (str "blocks/" page-id "/children"
                     (when cursor (str "?start_cursor=" cursor)))
            {:keys [results has_more next_cursor]} (:body (api-call url))]
        (if has_more
          (concat results
                  (page-blocks page-id next_cursor))
          results)))

    (defn delete-blocks
      [ids]
      (doseq [id ids]
        (println "deleting block: " id)
        (api-call :delete (str "blocks/" id))))

    (defn append-blocks
      [page-id blocks]
      (println "appending blocks: " blocks)
      (when (seq blocks)
        (api-call :patch
                  (str "blocks/" page-id "/children")
                  {:body {:children (vec blocks)}})))

    (defn replace-block
      [page-id block-id block]
      (let [deletable (drop-while (fn [block] (not (= (:id block) block-id)))
                                  (page-blocks page-id))
            rewritable (->> (next deletable)
                            reverse
                            (drop-while empty-block?)
                            reverse)]
        (append-blocks page-id [block])
        (delete-blocks (map :id deletable))
        (append-blocks page-id rewritable)))

    (defn delete-trailing-empty-blocks
      [page-id]
      (->> (reverse (page-blocks page-id))
           (take-while empty-block?)
           (map :id)
           delete-blocks)
      )

    #_(delete-trailing-empty-blocks "7cafec8d0a954cacb5f062ed25dd5c33")


    (comment :maybe-useless

             (defn db-pages-by-last-update
               [db-id]
               (-> (api-call :post
                             (str "databases/" db-id "/query")
                             {:body {:sorts [{:timestamp "last_edited_time" :direction "descending"}]}})
                   (get-in [:body :results])))

             #_(page-blocks (:id (first (db-pages-by-last-update "aef435c9307d467dbd32dd3a4b9a894d"))))))

(do :listener

    (defn listener_initial-state
      [opts]
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

    (defn listener_state-step
      [state block-changes]
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

    )

(do :commands

    (defn command-block?
      [block]
      (and (= "paragraph" (:type block))
           (let [first-text-sub-block (get-in block [:paragraph :text 0])]
             (and (= (:type first-text-sub-block) "text")
                  (let [sub-block-content (get-in first-text-sub-block [:text :content])]
                    (when-let [[_ command] (re-matches #"^#(.*)\.$" sub-block-content)]
                      (str/split command #" ")))))))



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
                                    (println "change!: " block)
                                    (when-let [[verb & args] (command-block? block)]
                                      (if-let [cmd (get commands (keyword verb))]
                                        (apply cmd block args))))}))))

(comment :replace-whole-page-xp

         (def id "91b4f44c2ddf483db595785ce8369a8f")

         (def page (api-call :get (str "pages/" id)))

         )


(comment :brute-api-wrap_aborted
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

(do :workona-export

    (defn entry-line [line]
      (if-let [[_ key val] (re-matches #"^(\S+): (.+) *$" line)]
        [key val]))

    (defn key-line [line]
      (if-let [[_ key] (re-matches #"^(\S+): *$" line)]
        key))

    (defn format-tree [tree]
      (cond (map? tree)
            (if (every? #(re-matches #"^[0-9]+$" %) (keys tree))
              (mapv (comp format-tree val) (sort-by key tree))
              (->> (map (fn [[k v]] [(keyword k) (format-tree v)]) tree)
                   (into {})))
            :else tree))

    (defn parse-line [line]
      (let [[_ indentation content] (re-matches #"^( *)(.*)$" line)]
        {:depth (/ (count indentation) 2)
         :content content}))

    (def tree
      (loop [lines (next (clojure.string/split-lines (slurp "/Users/pierrebaille/Desktop/workona-data-2021-09-22.txt")))
             path []
             tree {}]
        (if-not (seq lines)
          tree
          (let [[line & ls] lines
                ;; _ (println "line: " line)
                {:keys [depth content]} (parse-line line)
                current-depth (count path)]

            (if (< depth current-depth)
              (recur lines (vec (take depth path)) tree)
              (if-let [key (and ls
                                (not (>= depth (:depth (parse-line (first ls)))))
                                (key-line content))]
                (recur ls (conj path key) tree)
                (if-let [[k v] (entry-line content)]
                  (do #_(println "el " k v path tree) (recur ls path (assoc-in tree (conj path k) v)))
                  (if (key-line content)
                    (do #_(println "skip key no val " line)
                      (recur ls path tree))
                    [:pouet line (count ls)]))))))))

    (spit "workona.edn" (with-out-str (pprint (format-tree tree))))

    (def TREE (format-tree tree))
    (def BOOKMARK_FILE_PREFIX
      "<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<!-- This is an automatically generated file.\n     It will be read and overwritten.\n     DO NOT EDIT! -->\n<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n<TITLE>Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n")


    (defn str*
      ([xs] (if (sequential? xs)
              (apply str (map str* xs))
              (str xs)))
      ([x & xs]
       (str* (cons x xs))))

    (defn workspaces->chrome-html-bookmark-file-str [tree]
      (str*
        BOOKMARK_FILE_PREFIX
        "<DL><p>\n"
        (map (fn [{:keys [title tabs]}]
               ["    <DT><H3>" title "</H3>\n"
                "    <DL><p>\n" (map (fn [{:keys [title url]}]
                                 ["        <DT><A HREF=\"" url "\">" title "</A>\n"])
                               tabs)
                "    </DL><p>\n"])
             (:Workspaces tree))
        "</DL><p>\n"))

    (spit "export-workona.html" (workspaces->chrome-html-bookmark-file-str TREE))

    (type (:body (client/get "https://superuser.com/favicon.ico")))
    )