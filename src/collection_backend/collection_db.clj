(ns collection-backend.collection-db
  (:use [datomic.api :only [q db] :as d])
  (:require [clojure.java.io :as io]
            digest))

(def uri "datomic:mem://collection")

;(d/delete-database uri)
(d/create-database uri)

(def conn (d/connect uri))

;; setup the schema
(doseq [tx-data (read-string (slurp (io/resource "schema/collection.edn")))]
  (d/transact conn tx-data))

;; Create some dummy user
(def demo-user
  [{:db/id #db/id[:db.part/user]
    :user/username "demo"
    :user/password (digest/sha-256 "demo")
    :user/email "drchoc007@gmail.comzzz"}])

(defn create-user 
  "Creates the user!"
  ([username password email]
   (let [user [{:db/id #db/id[:db.part/user]
                :user/username username
                :user/password (digest/sha-256 password)
                :user/email email}]]
     (try 
       @(d/transact conn user)
       true
       (catch Exception e
         false)))))

(defn login [username-or-email password]
  (let [query  '[:find ?e
                 :in $ ?username-or-email ?type
                 :where [?e ?type ?username-or-email]]
        username-query (q query (db conn) username-or-email :user/username)
        email-query (q query (db conn) username-or-email :user/email)
        user-set (reduce into #{} [username-query email-query])]
    (if (zero? (count user-set))
      false
      (->>
        user-set
        (ffirst)
        (d/entity (db conn))
        (d/touch)
        (:user/password)
        (= (digest/sha-256 password))))))


(defn get-user-id [username]
  (->>
    (q
      '[:find ?e
        :in $ ?username :where [?e :user/username ?username]]
      (db conn)
      username)
    (ffirst)
    (d/entity (db conn))))

(defn user-exists? [username]
  (pos? (count (get-user-id username))))


(defn find-items [search-term]
  (let [rule '[[ (finditem ?item ?search) [(fulltext $ :item/title ?search) [[?item ?value]]] ]
               [ (finditem ?item ?search) [(fulltext $ :item/description ?search)  [[?item ?value]]] ] ]]
    (q
      '[:find ?item ?title ?description
        :in $ % ?search 
        :where (finditem ?item ?search) 
               [?item :item/title ?title]
               [?item :item/description ?description]]
      (db conn)
      rule
      search-term)))

(defn find-collections [search-term]
  (let [rule '[[ (findcoll ?collection ?search) [(fulltext $ :collection/title ?search) [[?collection ?value]]] ]
               [ (findcoll ?collection ?search) [(fulltext $ :collection/description ?search)  [[?collection ?value]]] ] ]]
    (q
      '[:find ?collection ?title ?description
        :in $ % ?search 
        :where (findcoll ?collection ?search) 
               [?collection :collection/title ?title]
               [?collection :collection/private false]
               [?collection :collection/description ?description]]
      (db conn)
      rule
      search-term)))

(defn get-user-items [username]
  (->>
    (get-user-id username)
    (:db/id)
    (q
      '[:find ?e ?title ?desc 
        :in $ ?username 
        :where [?e :item/owner ?username]
               [?e :item/title ?title]
               [?e :item/description ?desc]]
      (db conn))))

(defn get-user-collections [username]
  (->>
    (get-user-id username)
    (:db/id)
    (q
      '[:find ?e
        :in $ ?username 
        :where [?e :collection/owner ?username]
               [?e :collection/title ?title]
               [?e :collection/description ?desc]]
      (db conn))))

(defn check-user-items [username items]
  (let [userid (get-user-id username)]
    (reduce 
      #(and %1 (= userid %2))
      true 
      (map (comp :item/owner d/touch) items))))

      
(defn add-item [username item-title item-description item-img-url]
  (let [owner  (get-user-id username)
        tx-data [{:db/id #db/id[:db.part/user]
                  :item/title item-title
                  :item/description item-description
                  :item/url item-img-url
                  :item/owner (:db/id owner)}]]
    (d/transact conn tx-data)))

(defn add-collection 
  "Provide a vector of db/ids of collection items"
  [username collection-title collection-description collection-items]
  {:pre (check-user-items username collection-items)}
  (let [owner  (get-user-id username)
        tx-data [{:db/id #db/id[:db.part/user]
                  :collection/title collection-title
                  :collection/description collection-description
                  :collection/items collection-items
                  :collection/owner (:db/id owner)}]]
    (d/transact conn tx-data)))
 

(defn user-owns-collection? [username collection-id]
  (->>
    (d/entity (db conn) collection-id)
    (d/touch)
    (:collection/owner)
    (d/touch)
    (:user/username)
    (= username)))

(defn add-item-to-collection [username collection-id item-id]
  {:pre (user-owns-collection? username collection-id)}
  (d/transact
    conn
    [[:db/add collection-id
      :collection/items item-id]]))

(defn remove-item-from-collection [username collection-id item-id]
  {:pre (user-owns-collection? username collection-id)}
  (d/transact
    conn
    [[:db/retract collection-id
      :collection/items item-id]]))

(defn get-items-info [item-id-list]
  (map 
    (comp d/touch (partial d/entity (db conn))) 
    item-id-list))


(defn get-some-time-points []
  (let [tx-instants  (apply vector
                            (reverse (sort 
                                       (q 
                                         '[:find ?when :where [_ :db/txInstant ?when]]
                                         (db conn)))))]
  (map tx-instants (range 0 (count tx-instants) 1))))

(defn get-collection-as-of [collection-id as-of-time]
  (let [temporal-db (d/as-of (db conn) as-of-time)]
    (d/touch (d/entity temporal-db collection-id))))

(defn get-collection-over-time [collection-id]
  (map 
    (comp (partial get-collection-as-of collection-id) first)
    (get-some-time-points)))

  

(comment 
  (def old-db (d/as-of (db conn) (first (first tx-instants))))
  (d/touch
    (d/entity 
      old-db
      (:db/id (first (get-user-collections "marco"))))
    )
  (ffirst tx-instants)
  (def collection-4d (get-collection-over-time
    (:db/id (first (get-user-collections "marco")))))

  (count collection-4d)

  (conj [1] 1)
  (def all-tx (atom []))
  (swap! all-tx #(conj % 2))
  (def tx-instants 
    (apply vector
      (reverse (sort 
                 (q 
                   '[:find ?when :where [_ :db/txInstant ?when]]
                   (db conn))))))

  (map tx-instants (range 0 (count tx-instants) 5))


  ;;User is created
  (create-user "marco" "pass1" "something@cool.co")

  ;; This is how you add an item
  (add-item "marco" "Cool stamp Bro"  "sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullam" 
            "http://upload.wikimedia.org/wikipedia/commons/8/80/Albert_Einstein_1979_USSR_Stamp.jpg")
  (add-item "marco" "Better Stamp"    "i ratione voluptatem sequi nesciunt. Neque porro quisquam est, qui dolorem ipsum quia dolor sit amet, consectetur, adipisci velit, sed quia non numquam eius m" 
            "http://upload.wikimedia.org/wikipedia/commons/8/80/Albert_Einstein_1979_USSR_Stamp.jpg")
  (add-item "marco" "BestStamp"       "stiae consequatur, vel illum qui dolo"
            "http://upload.wikimedia.org/wikipedia/commons/8/80/Albert_Einstein_1979_USSR_Stamp.jpg")

  ;; Get all of the users items
  (type (get-user-items "marco"))

  ;; Lets add those items to our collection now
  ;; add the collection
  (add-collection   "marco" "My stamps" "They are sexy" (apply vector (map :db/id (get-user-items "marco"))))

  ;; Test to see if our check that a user owns a collection is a thing
  (user-owns-collection? "jane" (:db/id (first (get-user-collections "marco"))))

  ;; Lets add another item now
  (add-item "marco" "brand new stamp"       "lolz" "")
  (def new-item (ffirst (find-items "new")))

  ;; Add the item to the collection
  (add-item-to-collection
    "marco"
    (:db/id (first (get-user-collections "marco")))
    new-item)

  
  ;; Take that shit away
  (remove-item-from-collection
    "marco"
    (:db/id (first (get-user-collections "marco")))
    new-item)


 (map :item/title
    (map d/touch (:collection/items (first (get-user-collections "marco")))))


  ;; Get the user's collection
  (map (comp :item/title d/touch) (:collection/items (first (get-user-collections "marco"))))

  ;; Finding a collection
  (find-collection "sexy")


)

  ;;User is created
  (create-user "marco" "pass1" "something@cool.co")

  ;; This is how you add an item
  (add-item "marco" "Cool stamp Bro"  "sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullam" 
            "http://upload.wikimedia.org/wikipedia/commons/8/80/Albert_Einstein_1979_USSR_Stamp.jpg")
  (add-item "marco" "Better Stamp"    "i ratione voluptatem sequi nesciunt. Neque porro quisquam est, qui dolorem ipsum quia dolor sit amet, consectetur, adipisci velit, sed quia non numquam eius m" 
            "http://upload.wikimedia.org/wikipedia/commons/8/80/Albert_Einstein_1979_USSR_Stamp.jpg")
  (add-item "marco" "BestStamp"       "stiae consequatur, vel illum qui dolo"
            "http://upload.wikimedia.org/wikipedia/commons/8/80/Albert_Einstein_1979_USSR_Stamp.jpg")


(comment
  (use 'clojure.repl)
  (doc io/resource)

  )
