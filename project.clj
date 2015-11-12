(defproject com.charbeat.cljbeat/hll "1.0.0"
  :description "Hll implementation in clojure"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/math.numeric-tower "0.0.4"]
                 [pandect "0.4.1"]]

  :deploy-repositories [["releases" :clojars]]
  :signing {:gpg-key "F0903068"}

  :aot :all
  :vcs :git)
