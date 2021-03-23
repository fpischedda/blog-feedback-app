(ns app.handlers
  "Handlers of http requests, provides:
  - add-artcile-comment: to add a comment to an article
  - get-article-comments: to fetch all comments of an article
  "
  (:require [app.db :as db]))

(defn enable-article
  "Enable/disable an article.
  `article-id` is taken from `:path-params`.
  "
  [enabled request]
  (let [article-id (get-in request [:path-params :article-id])]
    (db/enable-article article-id enabled)
    {:status 204}))

(defn get-enabled-articles
  "Returns a list with all enabled articles at the `:articles` key.
  "
  [_request]
  {:status 200 :body {:articles (db/get-enabled-articles)}})

(defn- validate-request-comment
  "Takes a `request` map and try to extract a valid `comment`
  out of its `:body-params`, returning a map containing the
  `comment` key holding the comment obejct.
  If validation fails then return a map with an '`err` key,
  holding the validation error message.
  "
  [request]
  (let [{:keys [section-id author text parent-id]} (:body-params request)]
    (if (and section-id author text)
      {:comment
       {:section-id section-id
        :author     author
        :text       text
        :parent-id  parent-id}}
      {:err "missing some params"})))

(defn add-article-comment
  "Takes a `request`, parse/validate the request body
  and try to add a comment to the article identified by `article-id`.
  `article-id` is extracted from `:path-params`.
  If validation fails then return a `400` response with an `:error` key
  holding the error message.
  If the article is not enabled then fail silently returning a response
  body with a `nil` comment at the `:comment` key.
  In case of success the `:comment` key will hold the comment object.
  "
  [request]
  (let [article-id (get-in request [:path-params :article-id])
        {:keys [err comment]} (validate-request-comment request)]
    (if err
      {:status 400 :body {:error err}}
      {:status 201
       :body {:comment (db/add-article-comment article-id comment)}})))

(defn get-article-comments
  "Takes a request, expecting to have an `:article-id` in the
  `:path-params` and returns a list of comments attached to the article.
  If the article is not enable/does not exist then fails silently
  returning an empty list of comments.
  "
  [request]
  (let [article-id (get-in request [:path-params :article-id])]
    {:status 200 :body {:comments (or (db/get-article-comments article-id) [])}}))
