(ns app.routes
  (:require
   [muuntaja.core :as m]
   [reitit.coercion.spec :as rcs]
   [reitit.ring :as ring]
   [reitit.ring.coercion :as rrc]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [app.handlers :as handlers]))

(defn maybe-string? [x] (or (nil? x) (string? x)))

(def routes
  (ring/ring-handler
   (ring/router
    ["/api"
     ["/articles/:article-id"
      {:post   {:summary "Enable an article"
                :handler (partial handlers/enable-article true)}
       :delete {:summary "Disable an article"
                :handler (partial handlers/enable-article false)}}
      ["/comments" {:post {:summary "Add a comment to an article"
                           :parameters {:body {:parent-id maybe-string?
                                               :section-id string?
                                               :text string?
                                               :author maybe-string?}}
                           :handler handlers/add-article-comment}
                    :get   {:summary "Get all article's comments"
                            :handler handlers/get-article-comments}}]]
     ["/articles" {:get {:summary "Get a list of enabled articles"
                         :handler handlers/get-enabled-articles}}]]

    {:data {:muuntaja m/instance
            :coercion rcs/coercion
            :middleware [muuntaja/format-middleware
                         rrc/coerce-exceptions-middleware
                         rrc/coerce-request-middleware
                         rrc/coerce-response-middleware]}})))

(comment
  (routes {:request-method :get
           :headers {"Accept" "application/json"} :uri "/api/articles"})
  (routes {:request-method :get
           :headers {"Accept" "application/json"}
           :uri "/api/articles/abcd/comments"})
  (routes {:request-method :post, :uri "/api/articles/abcd/comments"})
  )
