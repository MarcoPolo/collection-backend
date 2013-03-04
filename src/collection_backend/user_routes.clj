(ns collection-backend.user-routes
  (:use compojure.core)
  (:require [compojure.route :as route]
            [collection-backend.collection-db :as c-db]
            [ring.middleware.session]
            [ring.util.response]
            [clojure.data.json :as json]))

(defroutes user-routes
  (POST "/login" [username password] 
    (->>
      (c-db/login username password)
      (assoc {} :login)
      (json/write-str)))
  (POST "/register" [username password email]
    (->>
      (c-db/create-user username password email)
      (assoc {} :login)
      (json/write-str)))
  (POST "/items" [username]
    (->>
      (c-db/get-user-items username)
      (json/write-str)))
  (POST "/collections" [username]
    (->>
      (c-db/get-user-collections username)
      (json/write-str)))
  (POST "/register" [username password email]
    (->>
      (c-db/create-user username password email)
      (assoc {} :login)
      (json/write-str)))
  (POST "/addItem" [username item-title item-description item-img-url]
    (->>
      (c-db/add-item username item-title item-description item-img-url)
      (assoc {} :login)
      (json/write-str)))
  (POST "/createCollection" [username collection-title collection-description item-ids]
    (->>
      (c-db/add-collection username collection-title collection-description item-ids)
      (json/write-str)))
  (POST "/addItemToCollection" [username collection-id item-id]
    (->>
      (c-db/add-item-to-collection username collection-id item-id)
      (json/write-str)))
  (POST "/removeItemFromCollection" [username collection-id item-id]
    (->>
      (c-db/remove-item-from-collection username collection-id item-id)
      (json/write-str)))
  (POST "/removeItemFromCollection" [username collection-id item-id]
    (->>
      (c-db/remove-item-from-collection username collection-id item-id)
      (json/write-str)))
  (POST "/getItemsinfo" [item-ids]
    (->>
      (c-db/get-items-info item-ids)
      (json/write-str)))
  (POST "/getCollectionOverTime" [collection-id]
    (->>
      (c-db/get-collection-over-time collection-id)
      (json/write-str))))
