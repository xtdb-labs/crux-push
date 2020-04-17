(ns work-bookings.core
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [crux.api :as crux]
            [crux.push :as cx-push]
            [juxt.jinx-alpha :as jinx]
            [juxt.jinx-alpha.resolve :as jinxresolv]
            [schema.core :as s]
            [yada.yada :as yada]))

(comment
  (with-open [node
              (crux/start-node {:crux.node/topology '[crux.standalone/topology]})]
    (let [ingestor
          (cx-push/create-ingestor
            {:schema
             (edn/read-string (slurp (io/resource "work_bookings/schema.edn")))
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
      (crux/sync node)
      (crux/q (crux/db node)
              '{:find [e]
                :where [[e :crux.db/id e]]
                :full-results? true}))))

(defn booking-resource [node]
  (let [ingestor
        (cx-push/create-ingestor
          {:schema (edn/read-string (slurp (io/resource "work_bookings/schema.edn")))
           :crux node})]
    (yada/resource
      {:methods
       {:get
        {:produces [{:media-type "text/plain"}]
         :response
         (fn [ctx]
           (let [alerts (crux/q
                          (crux/db node)
                          '{:find [e]
                            :where [[e :crux.db/id e]]})]
             (format "Total docs: %d\r\n" (count alerts))))}
        :post
        {:consumes [{:media-type "application/json"}
                    {:media-type "application/edn"}]
         :produces [{:media-type "application/json"}]
         :parameters {:body s/Any}
         :response
         (fn [ctx]
           (let [body (get-in ctx [:parameters :body])]
             (try
               (let [result (ingestor body)]
                 {:status 200
                  :documents result})
               (catch clojure.lang.ExceptionInfo e
                 (log/error e "Received invalid message" (ex-data e))
                 (if (= (:error (ex-data e)) :crux.push/invalid-message)
                   (assoc
                     (:response ctx)
                     :status 400
                     :body {"message" "Received invalid message"
                            "error" (ex-data e)})
                   (throw e))))))}}})))

(defn query-resource [node]
  (yada/resource
    {:methods
     {:post
      {:consumes [{:media-type "application/json"}
                  {:media-type "application/edn"}]
       :produces [{:media-type "application/json"}]
       :parameters {:body s/Any}
       :response
       (fn [ctx]
         (let [body (get-in ctx [:parameters :body])]
           {:status 200
            :result (crux/q (crux/db node) body)}))}}}))

(defn svr [node]
  (yada/listener ["/" [["ingest"
                        (#'work-bookings.core/booking-resource node)]
                       ["query"
                        (#'work-bookings.core/query-resource node)]]]
                 {:port 3000}))

(comment
  (def node (crux/start-node {:crux.node/topology '[crux.standalone/topology]}))
  (def ss (svr node))
  ((:close ss)))
