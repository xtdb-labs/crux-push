(ns work-bookings.core
  (:require [clojure.java.io :as io]
            [crux.push :as cx-push]
            [crux.api :as crux]
            [juxt.jinx-alpha :as jinx]
            [clojure.edn :as edn]
            [juxt.jinx-alpha.resolve :as jinxresolv]))

(comment (def node (crux/start-node {:crux.node/topology '[crux.standalone/topology]}))
         (def ingestor (cx-push/create-ingestor
                                 {:schema "work_bookings/schema.edn"
                                  :crux node})))

(let [{:keys [validated-message sub-schema]}
      (cx-push/validate-message
        {"code" "tmt"
         "name" "Tom"
         "manager" "00c5aa64-1b3f-48c9-99f4-3313e2d3526d"}
        (jinx/schema
          (edn/read-string
            (slurp (io/resource "work_bookings/schema.edn")))))]
  (cx-push/cruxify-message (:instance validated-message) sub-schema)
  #_(cx-push/crux-schemas sub-schema))

(cx-push/crux-schemas (get-in (jinxresolv/expand-document (jinx/schema (edn/read-string (slurp (io/resource "work_bookings/schema.edn")))) nil) ["anyOf" 0]))
