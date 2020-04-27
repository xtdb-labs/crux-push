# crux-push

WIP of combining jinx with crux into a JSON Schema driven ingestion library.

This library adds annotations to JSON Schema which are able to manipulate
submitted documents into Crux friendly structures.

See `example/work-bookings` for a demonstration

## Custom annotations

<dl>
<dt><code>crux:document</code></dt>
<dd>Decomposes an object into a separate document of its own.</dd>

<dt><code>crux:ref</code></dt>
<dd>Marks a string as a reference and will attempt to convert it into a
UUID.</dd>

<dt><code>crux:id</code></dt>
<dd>If this tag is included in a submitted object which has the JSON Schema
<code>crux:document</code> property, instead of generating a random UUID, the
id will be set as this string, converted into a UUID.</dd>

<dt><code>crux:type</code></dt>
<dd>This adds a <code>:crux-push/type</code> attribute to the submitted
document with the value of <code>crux:type</code>.</dd>

<dt><code>crux:containerAttribute</code></dt>
<dd>When included within an object schema, if the object is contained within an
array inside another object, the object inside the array will include an
additional attribute. This attribute will have the key of the
<code>crux:containerAttribute</code> value, and the value of the
<code>crux.db/id</code> of the parent object. I.e. The child document will
reference the parent.</dd>
</dl>

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

