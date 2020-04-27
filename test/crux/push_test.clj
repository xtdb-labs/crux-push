(ns crux.push-test
  (:require crux.push
            [juxt.jinx-alpha :as jinx]
            [clojure.test :as t]))

(def test-schema
  {"anyOf" [{"type" "object"
             "required" ["wingspan" "species"]
             "crux:document" true ; currently all root level objects are
             ; infered to be documents
             "crux:type" "duck" ; if it has a wingspan and a species, it's a duck
             "properties" {"wingspan" {"type" "string"}
                           "species" {"type" "string"}}}

            {"type" "object"
             "required" ["pawsize" "species" "homeAddress"]
             "crux:document" true
             "crux:type" "cat" ; if instead it has a pawsize, it's a cat
             "properties"
             {"pawsize" {"type" "string"}
              "species" {"type" "string"}
              "homeAddress"
              {"type" "object"
               "crux:document" true
               "crux:type" "address"
               "crux:containerAttribute" "homeAddress"
               "properties"
               {"line1" {"type" "string"}
                "line2" {"type" "string"}}}}}]})

(comment (def schema (jinx/schema test-schema))
         (def duck-result
           (crux.push/validate-message
             {"wingspan" "50cm"
              "species" "mallard"}
             schema))
         (def catt-result
           (crux.push/validate-message
             {"pawsize" "big"
              "species" "house-cat"
              "homeAddress"
              {"line1" "120"
               "line2" "Heeley Road"}}
             schema)))

(t/deftest small-schema-example-test
  (let [schema (jinx/schema test-schema)
        catt {"pawsize" "big"
              "species" "house-cat"
              "homeAddress"
              {"line1" "120"
               "line2" "Heeley Road"}}
        catt-result (crux.push/validate-message
                      catt
                      schema)
        duck {"wingspan" "50cm"
              "species" "mallard"}
        duck-result (crux.push/validate-message
                      duck
                      schema)]

    (t/testing "validation"
      (t/is (get-in duck-result [:validated-message :valid?]))
      (t/is (= "duck" (get-in duck-result [:sub-schema "crux:type"])))
      (t/is (thrown?
              Exception
              (crux.push/validate-message
                {"species" "mallard"}
                schema))))

    (t/testing "cruxify"
      ;; The document cruxified contains the same info
      (t/is
        (= duck
           (select-keys
             (let [{:keys [validated-message sub-schema]} duck-result]
               (crux.push/cruxify-message (:instance validated-message) sub-schema))
             ["species" "wingspan"])))

      (let [catt-crux
            (let [{:keys [validated-message sub-schema]} catt-result]
              (crux.push/cruxify-message (:instance validated-message) sub-schema))]
        ;; All documents with the "crux:document" true attribute have a crux.db/id
        (t/is (contains? catt-crux :crux.db/id))
        (t/is (contains? (get catt-crux "homeAddress") :crux.db/id))
        ;; 2 docs are produced here, and the cat links to its home
        (let [flat-catt (crux.push/flatten-crux-documents catt-crux)]
          (t/is (= 2 (count flat-catt)))
          (t/is (uuid? (some #(get % "homeAddress") flat-catt))))))))
