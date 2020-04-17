# crux-push

This is an WIP of combining jinx with crux into a JSONSchema driven ingestion
library.

See `example/work-bookings` for a demonstration

## Custom attributes

* `crux:document`
* `crux:ref`
* `crux:id`
* `crux:type`
* `crux:containerAttribute`

## Examples

```sh
curl -X POST localhost:3000/ingest -H 'Content-Type: application/edn' -d '
{"code" "mal" "name" "Malcolm" "crux:id" "1aa82dbf-03fa-4af5-9750-35d6a18ffe5e"}'

curl -X POST localhost:3000/ingest -H 'Content-Type: application/edn' -d '
{"employeeInfo" "1aa82dbf-03fa-4af5-9750-35d6a18ffe5e",
 "crux:id" "0cd75dbf-03fa-4af5-9750-35d6a18ffe4e"}'

curl -X POST localhost:3000/ingest -H 'Content-Type: application/edn' -d '
{"code" "tmt" "name" "Tom" "manager" "0cd75dbf-03fa-4af5-9750-35d6a18ffe4e"}'

curl -X POST localhost:3000/ingest -H 'Content-Type: application/edn' -d '
{"code" "dan" "name" "Dan" "manager" "0cd75dbf-03fa-4af5-9750-35d6a18ffe4e"}'

curl -X POST localhost:3000/ingest -H 'Content-Type: application/edn' -d '
{"employeeInfo" "0cd75dbf-03fa-4af5-9750-35d6a18ffe4e"
 "employees" [{"code" "hjy", "name" "Hugo"}
              {"code" "joa", "name" "Johanna"}
              {"code" "jon", "name" "Jon"
               "crux:id" "0cd75dbf-03fa-4af5-9750-35d6a18ffe4e"}]}'

curl -X POST localhost:3000/query -H 'Content-Type: application/edn' -d '
{:find [e]
 :where [[e :manager m]
         [m :employeeInfo i]
         [i :crux.db/id man]
         [man :name "Jon"]]
 :full-results? true}'
```

