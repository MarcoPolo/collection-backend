(ns collection-backend.handler
  (:use compojure.core
        collection-backend.user-routes )
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [collection-backend.collection-db :as c-db]
            [clojure.data.json :as json]))

user-routes
(defroutes app-routes
  (GET "/" [] "Backend works bro, but you need to use POST requests")
  (context "/user" [] user-routes)
  (POST "/search/items" [search-term] 
    (->>
      (c-db/find-items search-term)
      (json/write-str)))
  (POST "/search/collections" [search-term] 
    (->>
      (c-db/find-collections search-term)
      (json/write-str)))

  (route/not-found "Not Found"))

(def app
  (handler/site app-routes))
