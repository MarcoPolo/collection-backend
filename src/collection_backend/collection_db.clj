(ns collection-backend.collection-db
  (:use [datomic.api :only [q db] :as d])
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            digest))

;(def uri "datomic:mem://collection")
(def uri "datomic:free://localhost:4334/collection")

;(d/delete-database uri)
;(d/create-database uri)

(def conn (d/connect uri))

;; setup the schema
(comment
  (doseq [tx-data (read-string (slurp (io/resource "schema/collection.edn")))]
    (d/transact conn tx-data))
  )

;; Create some dummy user
(def demo-user
  [{:db/id #db/id[:db.part/user]
    :user/username "demo"
    :user/password (digest/sha-256 "demo")
    :user/email "drchoc007@gmail.comzzz"}])

(defn create-user 
  "Creates the user!"
  ([username password email url]
   (let [username (.toLowerCase username)
         user [{:db/id #db/id[:db.part/user]
                :user/username username
                :user/url url
                :user/password (digest/sha-256 password)
                :user/email email}]]
     (try 
       @(d/transact conn user)
       true
       (catch Exception e
         false)))))

(defn login [username-or-email password]
  (let [
        query  '[:find ?e ?username-or-email ?email ?url
                 :in $ ?username-or-email ?type
                 :where [?e ?type ?username-or-email]
                        [?e :user/url ?url]
                        [?e :user/email ?email]]
        username-query (q query (db conn) username-or-email :user/username)
        user-set (reduce into #{} [username-query])]
    (println "user" username-or-email "pass" password user-set username-query)
    (if (zero? (count user-set))
      false
      (if (->>
            user-set
            (ffirst)
            (d/entity (db conn))
            (d/touch)
            (:user/password)
            (= (digest/sha-256 password)))
        (zipmap [:id :username :email :url] (first user-set))
        false))))
        
        


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
      '[:find ?e ?title ?desc ?url ?items
        :in $ ?username 
        :where [?e :collection/owner ?username]
               [?e :collection/title ?title]
               [?e :collection/items ?items]
               [?e :collection/url ?url]
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
    (->
      (d/transact conn tx-data)
      (deref)
      (last)
      (second)
      (vals)
      (first))))



(defn add-collection 
  "Provide a vector of db/ids of collection items"
  [username collection-title collection-description is-private category collection-img-url collection-items]
  {:pre (check-user-items username collection-items)}
  (let [owner  (get-user-id username)
        tx-data [{:db/id #db/id[:db.part/user]
                  :collection/title collection-title
                  :collection/description collection-description
                  :collection/private is-private
                  :collection/url collection-img-url
                  :collection/type1 category
                  :collection/items collection-items
                  :collection/owner (:db/id owner)}]]
    (->
      (d/transact conn tx-data)
      (deref)
      (last)
      (second)
      (vals)
      (first))))
 

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
    (comp 
      #(select-keys % [:item/title :item/description :item/url])
      d/touch (partial d/entity (db conn))) 
    item-id-list))


(defn get-some-time-points []
  (let [tx-instants  (apply vector
                            (reverse (sort 
                                       (q 
                                         '[:find ?when :where [_ :db/txInstant ?when]]
                                         (db conn)))))]
  (map tx-instants (range 0 (count tx-instants) 1))))


(defn get-collection-items-with-db [collection-id db-conn]
  (->> 
    (map d/touch (:collection/items (d/entity db-conn collection-id)))
    (map #(select-keys % [:item/title :item/url :item/description :db/id]))))

(defn get-collection-items [collection-id]
  (get-collection-items-with-db collection-id (db conn)))

(defn get-collection-as-of [collection-id as-of-time]
  (let [temporal-db (d/as-of (db conn) as-of-time)]
    (get-collection-items-with-db collection-id as-of-time)))

(defn get-collection-over-time [collection-id]
  (map 
    (comp (partial get-collection-as-of collection-id) first)
    (get-some-time-points)))
  

(comment 

  (map d/touch (:collection/items (get-collection 17592186045445)))
  (json/write-str
    (map #(select-keys % [:item/title :item/url :item/description :db/id]) (get-collection-items-with-db 17592186045445 (db conn))))
    (get-collection-items-with-db 17592186045445 (db conn))




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
  (create-user "marco" "pass1" "something@cool.co" "https://fbcdn-profile-a.akamaihd.net/hprofile-ak-prn1/50220_247759031908649_1190234970_q.jpg")


  ;; This is how you add an item
  (def added-item
  (add-item "marco" "best thing ever"  "sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullam" 
            "http://upload.wikimedia.org/wikipedia/commons/8/80/Albert_Einstein_1979_USSR_Stamp.jpg"))
  (add-item "marco" "Better Stamp"    "i ratione voluptatem sequi nesciunt. Neque porro quisquam est, qui dolorem ipsum quia dolor sit amet, consectetur, adipisci velit, sed quia non numquam eius m" 
            "http://upload.wikimedia.org/wikipedia/commons/8/80/Albert_Einstein_1979_USSR_Stamp.jpg")
  (add-item "marco" "BestStamp"       "stiae consequatur, vel illum qui dolo"
            "http://upload.wikimedia.org/wikipedia/commons/8/80/Albert_Einstein_1979_USSR_Stamp.jpg")

  ;; 17592186045433
  
  (vals (second (last @added-item)))

  (get-user-items "marco")

  ;; Get all of the users items
  (map first (get-user-items "marco"))

  (get-user-items "marco")
  ;; Lets add those items to our collection now
  ;; add the collection
  (get-user-collections "marco")

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

  (def user-items (map first (get-user-items "marco")))
  user-items
  (str
    (apply vector
      (map 
        #(select-keys % [:item/title :item/description :item/url])
        (get-items-info user-items)
        )))


 (map :item/title
    (map d/touch (:collection/items (first (get-user-collections "marco")))))


  ;; Get the user's collection
  (map (comp :item/title d/touch) (:collection/items (first (get-user-collections "marco"))))

  ;; Finding a collection
  (find-collection "sexy")


)

(comment
  ;;User is created
  (create-user "marco" "pass1" "something@cool.co"
               "https://fbcdn-profile-a.akamaihd.net/hprofile-ak-prn1/50220_247759031908649_1190234970_q.jpg")
  (create-user "tasha" "pass2" "something-else@cool.com"
               "https://fbcdn-profile-a.akamaihd.net/hprofile-ak-prn1/50220_247759031908649_1190234970_q.jpg")

  (def collection-url "http://l1.yimg.com/bt/api/res/1.2/qI06Zg.7WLJLwUwi_GwDuQ--/YXBwaWQ9eW5ld3M7cT04NTt3PTYzMA--/http://media.zenfs.com/en-US/blogs/ygamesblog/ebay-game-collection.jpg")

  ;; This is how you add an item
  (add-item "marco" "Cool stamp Bro"  "sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullam" 
            "http://upload.wikimedia.org/wikipedia/commons/8/80/Albert_Einstein_1979_USSR_Stamp.jpg")
  (add-item "marco" "Better Stamp"    "i ratione voluptatem sequi nesciunt. Neque porro quisquam est, qui dolorem ipsum quia dolor sit amet, consectetur, adipisci velit, sed quia non numquam eius m" 
            "http://upload.wikimedia.org/wikipedia/commons/8/80/Albert_Einstein_1979_USSR_Stamp.jpg")
  (add-item "marco" "BestStamp"       "stiae consequatur, vel illum qui dolo"
            "http://upload.wikimedia.org/wikipedia/commons/8/80/Albert_Einstein_1979_USSR_Stamp.jpg")
  (add-collection   "marco" "My stamps" "They are sexy" false "stamps" collection-url (apply vector (map first (get-user-items "marco"))))
  (add-collection   "marco" "My Coins" "They are lolz" false "coins" collection-url (apply vector (map first (get-user-items "marco"))))
  (get-user-items "marco")
  (get-user-collections "marco")
  (use 'collection-backend.collection-db)
  (get-user-id "marco")

  (add-item "tasha" "Cool stamp Bro"  "sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullam" 
            "http://upload.wikimedia.org/wikipedia/commons/8/80/Albert_Einstein_1979_USSR_Stamp.jpg")
  (add-item "tasha" "Better Stamp"    "i ratione voluptatem sequi nesciunt. Neque porro quisquam est, qui dolorem ipsum quia dolor sit amet, consectetur, adipisci velit, sed quia non numquam eius m" 
            "http://upload.wikimedia.org/wikipedia/commons/8/80/Albert_Einstein_1979_USSR_Stamp.jpg")
  (add-item "tasha" "BestStamp"       "stiae consequatur, vel illum qui dolo"
            "http://upload.wikimedia.org/wikipedia/commons/8/80/Albert_Einstein_1979_USSR_Stamp.jpg")

  (add-collection   "tasha" "My other stamps" "They are sexy" collection-url (apply vector (map first (get-user-items "tasha"))))

(d/touch (get-user-id "tasha"))
  )

(comment
  (use 'clojure.repl)
  (doc io/resource)

  )
