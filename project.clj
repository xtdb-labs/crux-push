(defproject crux-push "0.0.1"
  :description ""
  :url "https://github.com/crux-labs/crux-push"
  :license {:name "MIT"}
  :source-paths ["src"]
  :profiles {:uberjar {:aot :all}}
  :deploy-repositories [["releases" :clojars]
                        ["snapshots" :clojars]])
