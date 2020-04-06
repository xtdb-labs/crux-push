(ns crux.push.core
  (:require
   [juxt.jinx-alpha.resolve :as jinxresolv]
   [clojure.walk :refer [keywordize-keys]]
   [crux.api :as crux]
   [clojure.edn :as edn]
   [juxt.jinx-alpha :as jinx]
   [clojure.java.io :as io]))


(defn key-comparator []
  (let [score (fn [k]
                (cond
                  (= k :crux.db/id) 1
                  (keyword? k) 2
                  :else 3))]
    (fn [x y]
      (let [r (compare (score x) (score y))]
        (if (zero? r)
          (compare x y)
          r)))))

(defn split-on-subdocs [doc]
  (lazy-seq
   (cons
    (dissoc doc :crux/subdocs)
    (mapcat split-on-subdocs (:crux/subdocs doc)))))

(defn crux-schemas [schema]
  (filter
   (fn [[_ v]]
     (or
      (contains? v "crux:document")
      (and (= (get v "type") "array")
           (let [items (get v "items")]
             ;; we don't currently support the array form of items.
             (not (sequential? items))
             (contains? items "crux:document")))))
   (get schema "properties")))

(comment
  (->
   "rlws/iot_cloud/schema.edn"
   io/resource
   slurp
   edn/read-string
   jinx/schema
   (jinxresolv/expand-document nil)
   (get-in ["anyOf" 1])
   (get-in ["properties" "dealership"])
   (get-in ["properties" "customer"])
   (get-in ["properties" "logicalGateway"])
   crux-schemas))

(defn cruxify-message [message schema]
  (let [cruxify
        (fn this [doc schema]
          (let [id (java.util.UUID/randomUUID)
                typ (get schema "rlws:type")
                document-ref (get schema "crux:documentRef")
                doc (into
                     ;; This sorted-map-by might be something to
                     ;; toggle in options
                     (sorted-map-by (key-comparator))
                     (cond-> doc
                       id (assoc :crux.db/id id)
                       typ (assoc :rlws/type typ)
                       document-ref (assoc "crux:documentRef" document-ref)))]

            (reduce
             (fn [doc [propname {typ "type" :as subschema}]]
               (case typ
                 "object"
                 (if-let [subdoc (get doc propname)]
                   (let [component-of (get subschema "crux:containerAttribute")
                         document-ref (get subschema "crux:documentRef")
                         subdoc (cond->
                                    (this subdoc subschema)
                                    component-of (assoc component-of id)
                                    document-ref (assoc "crux:documentRef" document-ref))]

                     (-> doc
                         ;; The nested subdoc is removed
                         ;;(dissoc propname)
                         ;; The subdoc is stored in a hierarchical
                         ;; structure are will be flattened into a
                         ;; document sequence later (by crux/subdocs)
                         (assoc propname subdoc)))
                   doc)

                 "array"
                 (let [subschema (get subschema "items")
                       document-ref (get subschema "crux:documentRef")
                       component-of (get subschema "crux:containerAttribute")
                       subdocs
                       ;; Relieved that jinx will have ensured this is a seq!
                       (for [subdoc (get doc propname)]
                         (cond-> (this subdoc subschema)
                           component-of (assoc component-of id)
                           document-ref (assoc "crux:documentRef" document-ref)))]
                   (-> doc
                       (assoc propname (vec subdocs))))
                 doc))
             doc
             (crux-schemas schema))))]
    (cruxify message schema)))

(defn validate-message [message schema]
  (let [validation (jinx/validate message schema)]
    (if (:valid? validation)
      (:instance validation)
      (throw
       (ex-info
        "Not valid!"
        {:error :rlws/invalid-message
         :validation validation})))))

(defn flatten-crux-documents
  [input]
  (let [f (fn this [m]
            (cond
              (map? m)
              ;; We have a map to return, but first we need to remove
              ;; some submaps
              (reduce
               (fn [acc [k v]]
                 (cond
                   (map? v)
                   (let [nested-map (this v)
                         nested-map-id (:crux.db/id nested-map)]

                     (if nested-map-id
                       (-> acc
                           (update :crux/subdocs concat (cons
                                                         (dissoc nested-map :crux/subdocs)
                                                         (:crux/subdocs nested-map)))
                           (dissoc k))
                       acc))

                   (sequential? v)
                   (let [{:keys [subdocs items]}
                         (reduce
                          (fn [acc item]
                            (let [nested-map (this item)
                                  _ (prn nested-map)
                                  nested-map-id (:crux.db/id item)]

                              (if nested-map-id
                                (-> acc
                                    (update :subdocs (fnil conj []) nested-map)
                                    (dissoc k))
                                (update acc :items (fnil conj []) nested-map))))
                          {}
                          v)]
                     (cond-> acc
                       items (assoc k items)
                       subdocs (update :crux/subdocs concat (map #(dissoc % :crux/subdocs) subdocs))))
                   :else acc))
               m m)
              :else m))
        res (f input)]
    ;; We're at the top of the hierarchy, so we can shallow flatten
    ;; the subdocs.
    (cons (dissoc res :crux/subdocs)
          (:crux/subdocs res))))
