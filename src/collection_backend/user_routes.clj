(ns collection-backend.user-routes
  (:use compojure.core
        (sandbar stateful-session))
  (:require [compojure.route :as route]
            [collection-backend.collection-db :as c-db]
            [ring.middleware.session]
            [ring.util.response]
            [clojure.data.json :as json]
            digest))

(def custom-session (atom {}))

(defn get-auth-token []
  (digest/sha-256 (str (int (rand 10000)))))


(comment
  (read-string "false")

  (let [username "marco"
        item-title "asdfff"
        item-description "jdsfjdsj"
        item-img-url "ajsdfjdj"
        collection-title "dfj"
        collection-description "asdjfjfj"
        is-private false
        category "stamps"
        img-url "fdjfjd"
        item-ids [17592186045453]]

    (->>
      (c-db/add-collection username collection-title collection-description is-private category img-url item-ids)
      (assoc {:success true} :id)
      (json/write-str))

    )


  (

    (json/write-str
      (assoc {} :id
        (c-db/add-item username item-title item-description item-img-url))))

  )

(defroutes user-routes
  (POST "/login" [username password] 
    (let [logged-in (c-db/login username password)]
      (if logged-in (session-put! :username username))
      (json/write-str
        (if logged-in
          {:login true
           :user logged-in}
          {:login false
           :user nil}))))
  (POST "/whoami" [] 
    (println "session" (session-get :username))
    (json/write-str (session-get :username nil)))
  (POST "/register" [username password email]
    (->>
      (c-db/create-user username password email)
      (assoc {} :register)
      (json/write-str)))
  (POST "/items" [username]
    (->>
      (c-db/get-user-items username)
      (map (partial zipmap [:id :title :description]))
      (json/write-str)))
  (POST "/collections" [username]
    (->>
      (c-db/get-user-collections username)
      (map (partial zipmap [:id :title :description :img-url]))
      (json/write-str)))
  (POST "/collectionInfo" [collection-id]
    (->>
      (c-db/get-collection-items collection-id )
      (json/write-str)))
  (POST "/addItem" [username item-title item-description item-img-url]
    (println username " has created item " item-title)
    (->>
      (c-db/add-item username item-title item-description item-img-url)
      (assoc {:success true} :id)
      (json/write-str)))
  (POST "/createCollection" [username collection-title collection-description is-private category img-url item-ids]
    (println username " has created the collection " collection-title)
    (let [is-private (read-string is-private)
          item-ids (read-string item-ids)]
      (->>
        (c-db/add-collection username collection-title collection-description is-private category img-url item-ids)
        (assoc {:success true} :id)
        (json/write-str))))
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
