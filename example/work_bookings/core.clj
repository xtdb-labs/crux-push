(ns work-bookings.core
  (:require [clojure.java.io :as io]
            [crux.push :as cx-push]
            [crux.api :as crux]
            [juxt.jinx-alpha :as jinx]
            [clojure.edn :as edn]
            [juxt.jinx-alpha.resolve :as jinxresolv]))

(with-open [node (crux/start-node {:crux.node/topology '[crux.standalone/topology]})]
  (let [ingestor
        (cx-push/create-ingestor
          {:schema (edn/read-string (slurp (io/resource "work_bookings/schema.edn")))
           :crux node})
        ;; Add admin user and take uuid
        mal (ingestor {"code" "mal"
                       "name" "Malcolm"})
        ;; Make mal a manager
        mal-manager (ingestor {"employeeInfo" (str (get mal :crux.db/id))})
        ;; Give mal some employees
        tmt (ingestor {"code" "tmt"
                       "name" "Tom"
                       "manager" (str (get mal-manager :crux.db/id))})
        dan (ingestor {"code" "dan"
                       "name" "Dan"
                       "manager" (str (get mal-manager :crux.db/id))})
        ;; Make jon and employees in one go
        jon-manager (ingestor {"employeeInfo" "0cd75dbf-03fa-4af5-9750-35d6a18ffe4e"
                               "employees" [{"code" "hjy", "name" "Hugo"}
                                            {"code" "joa", "name" "Johanna"}
                                            {"code" "jon", "name" "Jon", "crux:id" "0cd75dbf-03fa-4af5-9750-35d6a18ffe4e"}]})]
    jon-manager))

