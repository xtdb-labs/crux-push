(ns crux.push
  (:require
    [juxt.jinx-alpha.resolve :as jinxresolv]
    [clojure.walk :refer [keywordize-keys]]
    [crux.api :as crux]
    [clojure.edn :as edn]
    [juxt.jinx-alpha :as jinx]
    [clojure.java.io :as io])
  (:import (java.util UUID)))

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

(defn crux-schemas [schema]
  (filter
    (fn [[_ v]]
      (or
        (contains? v "crux:document")
        (contains? v "crux:ref")
        (and (= (get v "type") "array")
             (let [items (get v "items")]
               ;; we don't currently support the array form of items.
               (not (sequential? items))
               (contains? items "crux:document")))))
    (get schema "properties")))

(defn validate-message [message schema]
  (let [exp-schema (get (jinxresolv/expand-document schema nil) "anyOf")
        result (reduce
                 (fn [errors ss]
                   (let [validation (jinx/validate message ss)]
                     (if (:valid? validation)
                       (reduced {:validated-message validation
                                 :sub-schema ss
                                 :success true})
                       (conj errors validation))))
                 [] exp-schema)]
    (if (:success result)
      result
      (throw (ex-info "Invalid Message Exception: The message did not match the supplied schema"
                      {:error ::invalid-message
                       :validation result})))))

(defn cruxify-message [message schema]
  (let [cruxify
        (fn this [doc schema]
          (let [id (if (get doc "crux:id")
                     (UUID/fromString (get doc "crux:id"))
                     ;; may need this crux id to be a string
                     (UUID/randomUUID))
                crux-type (get schema "crux:type")
                doc (into
                      ;; This sorted-map-by might be something to
                      ;; toggle in options
                      (sorted-map-by (key-comparator))
                      (cond-> doc
                        id (-> (assoc :crux.db/id id) (dissoc "crux:id"))
                        crux-type (assoc ::type crux-type)))]

            (reduce
              (fn [doc [propname {typ "type" :as subschema}]]
                (case typ
                  "object"
                  (if-let [subdoc (get doc propname)]
                    (let [component-of (get subschema "crux:containerAttribute")
                          subdoc (cond->
                                   (this subdoc subschema)
                                   component-of (assoc component-of id))]

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
                        component-of (get subschema "crux:containerAttribute")
                        subdocs
                        ;; Relieved that jinx will have ensured this is a seq!
                        (for [subdoc (get doc propname)]
                          (cond-> (this subdoc subschema)
                            component-of (assoc component-of id)))]
                    (-> doc
                        (assoc propname (vec subdocs))))

                  "string"
                  (if true
                    ;; TODO Check to see if ref is correct
                    (update doc propname (fn [id] (when id (UUID/fromString id))))
                    doc)
                  
                  doc))
              doc
              (crux-schemas schema))))]
    (cruxify message schema)))

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
                            (update ::subdocs concat (cons
                                                       (dissoc nested-map ::subdocs)
                                                       (::subdocs nested-map)))
                            (dissoc k))
                        acc))

                    (sequential? v)
                    (let [{:keys [subdocs items]}
                          (reduce
                            (fn [acc item]
                              (let [nested-map (this item)
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
                        subdocs (update ::subdocs concat (map #(dissoc % :crux/subdocs) subdocs))))
                    :else acc))
                m m)
              :else m))
        res (f input)]
    ;; We're at the top of the hierarchy, so we can shallow flatten
    ;; the subdocs.
    (cons (dissoc res ::subdocs)
          (::subdocs res))))

(defn create-ingestor
  [{:keys [crux schema]}]
  (let [schema (jinx/schema (edn/read-string (slurp (io/resource schema))))]
    (fn [raw-docs]
      (let [{:keys [validated-message sub-schema]} (validate-message raw-docs schema)
        docs (cruxify-message (:instance validated-message) sub-schema)
        flat-docs (flatten-crux-documents docs)]
    (crux/submit-tx
      crux
      (vec (for [doc (map keywordize-keys flat-docs)]
             [:crux.tx/put doc])))
    docs))))
