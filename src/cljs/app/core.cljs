(ns app.core
  "Example application that shows a basic use of Datascript.
  This application give the opportunity to provide feedback to single
  sections of an article and to reply to previous messages.
  On start, the application will look for all the elements with a specific
  class and attatches the feedback functionality to them, which
  will offer the following options:
  - show the number of messages for each section
  - read previous messages
  - add new feedback messages to the section
  - reply to other messages

  to keep everything super simple, the application is based on the format of
  the articles of my blog:

  <div class='body'>
    <div class='section' id='section-title'>
      <h2>Section title</h2>
      <p>Text<p/>
    </div>
  </div>
 "
  (:require
   [cljs.core :refer [random-uuid]]
   [cljs.core.async :refer [<! put! chan go]]
   [goog.dom :as dom]
   [rum.core :as rum]
   [app.db :as db]))

;; Channel used to simulate communication with a server.
;; When creating or deliting a message, a new event :add or :del
;; will be put to `event-ch`.
;; A listener will receive these events and will update the database
;; which then will trigger a re-render of the page.
(defonce event-ch (chan))

(defmulti handle-event :event)

(defmethod handle-event :add [event]
  (db/add-comment! (:payload event)))

(defmethod handle-event :del [event]
  (db/delete-comment! (:payload event)))

(defmethod handle-event :quit! [_event]
  (js/console.log "Received :quit! message, stopping listener"))

;; prevents to start another listener on code reload after recompilation
(defonce event-listener_ (atom nil))

(defn start-listener!
  "Starts a go block with a loop which will listen for events.
   Events are:
   - :add : new message, :payload will contain the message map
   - :del : message has been deleted, :payload will contain the message id
   - :quit : for some reason the server decided to quit
   For each new mes"
  []
  (when-not @event-listener_
    (go (loop []
          (let [{:as e :keys [event payload]} (<! event-ch)]
            (handle-event e)
            (when-not (= event :quit)
              (recur))))
        (reset! event-listener_ nil))
    (reset! event-listener_ true)))

;; UI code
(defn toggle-area
  [area-id]
  (-> area-id
    dom/getElement
    .-classList
    (.toggle "invisible")))

(defn clear-feedback-form
  [section-id]
  (let [author (dom/getElement (str "feedback-author-" section-id))
        text (dom/getElement (str "feedback-text-" section-id))]
    (aset author "value" "")
    (aset text "value" "")))

(defn send-message
  "Simulate sending a message to a server.
   A listener will get messages from the chan and will update the database.
   Keys `:id` and `:created-at` are added here but it should be the
   responsibility of an eventual backend application."
  [message]
  (put! event-ch {:event :add
                  :payload (assoc message
                             :id (random-uuid)
                             :created-at (db/now-str))}))

(defn delete-message [id]
  (put! event-ch {:event :del :payload id}))

(defn add-feedback
  "extracts author and text from section form and add a new
  comment to the database.
  the use of `(or comment-id section-id)` is not super nice but at least
  I don't need to have separate functions for root comments and replies"
  ([section-id]
   (add-feedback section-id nil))
  ([section-id comment-id]
   (let [author (-> (str "feedback-author-" (or comment-id section-id))
                  dom/getElement
                  .-value)
         text (-> (str "feedback-text-" (or comment-id section-id))
                dom/getElement
                .-value)]
     (send-message {:section-id section-id
                    :parent comment-id
                    :author author :text text})
     (clear-feedback-form (or comment-id section-id)))))

(rum/defc message-commands
  "Component that renders delete and reply commands"
  [section-id comment-id]
  (let [area-id (str "reply-to-" comment-id)]
    [:div
     [:button {:on-click (fn [_] (toggle-area area-id))} "reply"]
     [:button {:on-click (fn [_] (delete-message comment-id))} "delete"]
     [:div {:class "invisible"
            :id area-id}
      [:div
       [:label "Author: "] [:br]
       [:input {:id (str "feedback-author-" comment-id)}]]
      [:div
       [:label "Text: "] [:br]
       [:textarea {:id (str "feedback-text-" comment-id) :rows 5 :cols 20}]]
      [:div
       [:button {:on-click (fn [_] (clear-feedback-form comment-id))} "Clear"]
       [:button {:on-click (fn [_]
                             (add-feedback section-id comment-id)
                             (toggle-area area-id))} "Send"]]]]))

(defn sorted [comments]
  (sort-by :comment/created-at < comments))

(rum/defc feedback-message
  "Render the message and all its replies"
  [db section-id {:comment/keys [id author created-at text]} indent]
  (let [replies (sorted (db/comment-replies db id))]
    [:div {:id id :key id
           :style {:background-color "#fafefa"
                   :padding "8px"
                   :margin-left (str (* indent 16) "px")
                   :border-radius "8px"}}
     [:label (str "- by " author)]
     [:small {:style {:padding-left "8px"}} (str "at " created-at)]
     [:p text]
     (message-commands section-id id)
     (mapv (fn [c] (feedback-message db section-id c (inc indent))) replies)]))

(rum/defc feedback-component < rum/reactive
  "feedback area is composed by an input text for the author,
   a textarea for the message and a div containing all previous messages
   for the current section identified by `section-id`"
    [section-id db]
    (let [db (rum/react db)
          area-id (str "feedback-area-" section-id)
          comments (sorted (db/section-comments db section-id))
          comment-count (db/section-comments-count db section-id)]
      [:div {:key section-id
             :style {:padding "8px"}}
       [:button {:on-click (fn [_] (toggle-area area-id))}
        (str "Write your feedback (" comment-count ")")]
       [:div {:class "invisible" :id area-id}
        [:div
         [:label "Author: "] [:br]
         [:input {:id (str "feedback-author-" section-id)}]]
        [:div
         [:label "Text: "] [:br]
         [:textarea {:id (str "feedback-text-" section-id) :rows 5 :cols 20}]]
        [:div
         [:button {:on-click (fn [_] (clear-feedback-form section-id))} "Clear"]
         [:button {:on-click (fn [_] (add-feedback section-id))} "Send"]]
        [:div
         [:p (str "Previous messages (" comment-count ")")]
         [:div (mapv (fn [c] (feedback-message db section-id c 0)) comments)]]]]))

(defn mount-feedback-component
  [section-id container db]
  (rum/mount (feedback-component section-id db) container))

(defn add-section-to-db
  "parse a section element to extract section id and name and add it
   to the database"
  [element]
  (let [id (.getAttribute element "id")
        name (-> element .-children (aget 0) .-textContent)]
    (db/add-section! {:id id :name name})))

(defn attach-feedback-component-to-section
  "add a div container for the feedback component and attach a component to it;
  the component will receive the app state which will be watched to react to
  changes"
  [section-element db]
  (let [id (.getAttribute section-element "id")
        container-id (str "feedback-container-" id)
        prev-container (dom/getElement container-id)
        container (or
                    prev-container
                    (dom/createDom "div" (clj->js {:id container-id})))]
    (add-section-to-db section-element)
    (when-not prev-container
      (.insertBefore section-element container (-> section-element
                                                 .-children
                                                 (aget 1))))
    (mount-feedback-component id container db)))

(defn init-app
  "fetches all elements with a section class, parse all sections,
   remove prevoius feedback related elements (useful when reloading script)
   and add them to the db, finally attach a `feedback` button to all
   sections' container div"
  [db]
  (let [sections (dom/getElementsByClass "section")]
    (doseq [section sections]
      (attach-feedback-component-to-section section db))
    (start-listener!)))

(init-app db/db)

(comment
  (db/add-mock-data!)
  )
