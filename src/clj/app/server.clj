(ns app.server
  (:require
   [ring.adapter.jetty :as jetty]
   [app.routes :refer [routes]])
  (:gen-class))

(def server_ (atom nil))

(defn start-server!
  [port]
  (reset! server_ (jetty/run-jetty #'routes {:port port, :join? false})))

(defn stop-server!
  []
  (swap! server_ (fn [server] (.stop server))))

(comment
  (start-server! 3000)
  (stop-server!)
  )

(defn -main [& args]
  (start-server! (-> (nth args 1 "3000") (Integer/parseInt))))
