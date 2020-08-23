(ns app.db
  (:require
   [cljs.core :refer [random-uuid]]
   [datascript.core :as d]))

(def schema
  {:section/id {:db/unique :db.unique/identity}
   :comment/id {:db/unique :db.unique/identity}
   :comment/section {:db/valueType :db.type/ref
                     :db/cardinality :db.cardinality/one}
   :comment/parent {:db/valueType :db.type/ref
                    :db/cardinality :db.cardinality/one}})

(def db (d/create-conn schema))

;; some ids, pre-generated to create mock data and references
(defonce ids {:section1 "overview"
              :section2 "application-design"
              :comment1 (random-uuid)
              :comment2 (random-uuid)
              :comment3 (random-uuid)
              :comment4 (random-uuid)})

(defn add-mock-data! []
  (let [{:keys [section1
                comment1 comment2 comment3 comment4]} ids]
    (d/transact! db
      [{:section/id section1
        :section/name "Overview"}
       {:comment/id comment1
        :comment/author "reviewer"
        :comment/section {:section/id section1}
        :comment/created-at "2020-08-17T08:41:12Z"
        :comment/text "Comment by reviewer"}
       {:comment/id comment2
        :comment/parent {:comment/id comment1}
        :comment/author "author"
        :comment/section {:section/id section1}
        :comment/created-at "2020-08-18T08:41:12Z"
        :comment/text  "Author's reply"}
       {:comment/id comment3
        :comment/parent {:comment/id comment2}
        :comment/section {:section/id section1}
        :comment/author "reviewer"
        :comment/created-at "2020-08-18T09:11:02Z"
        :comment/text "Reviewer's reply"}
       {:comment/id comment4
        :comment/parent {:comment/id comment1}
        :comment/section {:section/id section1}
        :comment/author "reviewer"
        :comment/created-at "2020-08-18T09:11:02Z"
        :comment/text "Reviewer's reply"}
       ])))

(comment
  (add-mock-data!)
  )

(defn add-section! [{:keys [id name]}]
  (d/transact! db
    [{:section/id id
      :section/name name}]))

(defn all-sections [db]
  (d/q '[:find [(pull ?s [:section/id :section/name]) ...]
         :where
         [?s :section/id ?id]
         [?s :section/name ?name]
         ]
    db))

(comment
  (all-sections @db) ;; => #{["overview" "Overview"] ["application-design" "Application design"]}
  )

(defn all-comments [db]
  (d/q '[:find ?id ?author ?created-at ?text ?section-id ?section-name
         :where
         [?c :comment/id ?id]
         [?c :comment/author ?author]
         [?c :comment/created-at ?created-at]
         [?c :comment/text ?text]
         [?c :comment/section ?s]
         [?s :section/id ?section-id]
         [?s :section/name ?section-name]
         ]
    db))

(comment
  (all-comments @db)
  )

(defn now-str []
  (.toISOString (js/Date.)))

(defn add-comment! [{:keys [id section-id author text created-at parent]
                     :or {id (random-uuid)
                          created-at (now-str)}}]
  (let [comment {:comment/id id
                 :comment/author author
                 :comment/created-at created-at
                 :comment/text text
                 :comment/section {:section/id section-id}}]
    (d/transact! db
      [(if parent
         (assoc comment :comment/parent {:comment/id parent})
         comment)])))

(defn delete-comment!
  "Soft deletes a comment, changing the text to [DELETED]"
  [id]
  (d/transact! db
    [{:comment/id id
      :comment/text "[DELETED]"}]))

(comment
  (add-comment! {:section-id (:section1 ids)
                 :author "Diddi"
                 :text "Miaouuuuu"})
  )

(defn section-comments
  "This query will return all 'root level' comments for a section.
   I have tried very hard to have one query that will return all root comments
   with all their replies but I have failed, for this reason I have splitted
   the query into two: 'get root comments' and 'get comment replies."
  [db section-id]
  (d/q '[:find [(pull ?c [:comment/id :comment/author
                          :comment/text :comment/created-at]) ...]
         :in $ ?section-id
         :where
         [?c :comment/id ?id]
         [(missing? $ ?c :comment/parent)]
         [?c :comment/section ?s]
         [?s :section/id ?section-id]
         ]
    db section-id))

(comment
  (section-comments @db (:section1 ids))
  )

(defn section-comments-count
  "If the query cannot find any entity that matches then it cannot reasonably
   count anything and it will return nil; with the use of `or` we can handle
   this case and return 0."
  [db section-id]
  (or
    (d/q '[:find (count ?c) .
             :in $ ?section-id
             :where
             [?s :section/id ?section-id]
             [?c :comment/section ?s]
             ]
        db section-id)
    0))

(comment
  (add-comment! {:section-id "a" :author "aaaa" :text "ttttt"})
  (add-comment! {:section-id "a" :author "bbbb" :text "mmmm"})
  (add-comment! {:section-id "a" :author "cccc" :text "ssss"})
  (add-comment! {:section-id "b" :author "aaaa" :text "ttttt"})
  (add-comment! {:section-id "b" :author "bbbb" :text "mmmm"})

  (section-comments-count @db "a")
  (section-comments-count @db "b")
  )

(defn comment-replies [db comment-id]
  (d/q '[:find [(pull ?c [:comment/id :comment/author
                          :comment/text :comment/created-at]) ...]
         :in $ ?parent-id
         :where
         [?c :comment/id ?id]
         [?c :comment/parent ?p]
         [?p :comment/id ?parent-id]
         ]
    db comment-id))

(comment
  (section-comments @db "foreword")
  (comment-replies @db (-> (section-comments @db "foreword") first first))
  )
