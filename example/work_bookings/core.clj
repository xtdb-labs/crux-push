(ns work-bookings.core
  "Contains an example API endpoint for a internal work holiday booking system"
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :as t]
            [clojure.tools.logging :as log]
            [crux.api :as crux]
            [crux.push :as cx-push]
            [juxt.jinx-alpha :as jinx]
            [juxt.jinx-alpha.resolve :as jinxresolv]
            [schema.core :as s]
            [yada.yada :as yada]))

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
         (fn [_]
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
       :response (fn [ctx]
                   (let [body (get-in ctx [:parameters :body])]
                     {:status 200
                      :result (crux/q (crux/db node) body)}))}}}))

(defn svr [node]
  (let [s (yada/listener ["/" [["ingest"
                                (#'work-bookings.core/booking-resource node)]
                               ["query"
                                (#'work-bookings.core/query-resource node)]]]
                         {:port 3000})]
    (reify java.io.Closeable
      (close [_]
        (.close node)
        ((:close s))))))

(comment
  ;; start server
  (def node (crux/start-node {:crux.node/topology '[crux.standalone/topology]}))
  (def ss (svr node))
  (.close ss))

(t/deftest holiday-system-test
  (with-open [node (crux/start-node
                     {:crux.node/topology
                      '[crux.standalone/topology]})]
    (let [ingestor
          (cx-push/create-ingestor
            {:schema
             (edn/read-string
               (slurp
                 (io/resource "work_bookings/schema.edn")))
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
          jon (ingestor
                {"code" "jon", "name" "Jon"})
          ;; Make jon and employees in one go
          jon-manager (ingestor {"employeeInfo" (str (get jon :crux.db/id))
                                 "employees" [{"code" "hjy", "name" "Hugo"}
                                              {"code" "joa", "name" "Johanna"}]})]
      (crux/sync node)

      ;; Employees are tied to their managers
      (t/is #{["Dan"] ["Tom"]}
            (crux/q (crux/db node)
                    '{:find [n]
                      :where [[m :crux.push/type "manager"]
                              [m :employeeInfo ei]
                              [ei :code "mal"]
                              [e :manager m]
                              [e :name n]]}))
      (t/is #{["Hugo"] ["Johanna"]}
            (crux/q (crux/db node)
                    '{:find [n]
                      :where [[m :crux.push/type "manager"]
                              [m :employeeInfo ei]
                              [ei :code "jon"]
                              [e :manager m]
                              [e :name n]]})))))
