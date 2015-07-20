(defproject com.charbeat.cljbeat/hll "1.0.0-SNAPSHOT"
  :description "Hll implementation in clojure"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/math.numeric-tower "0.0.4"]
                 [pandect "0.4.1"]]

  ; This "plugins" line is required in order to push builds to the repo
  ; which we store in an S3 bucket.
  :plugins [[s3-wagon-private "1.1.2"]]

  :repositories [["releases" {:url "https://jars.chartbeat.com:7443/releases/"}]
                 ["snapshots" {:url "https://jars.chartbeat.com:7443/snapshots/"}]]

  ;; This points to the repositories where we fetch chartbeat-written
  ;; dependencies. I've set up an S3 bucket named 'chartbeat-jars' to
  ;; hold our jars.
  :deploy-repositories [["releases" {:url "s3p://chartbeat-jars/releases/"
                                     :sign-releases false}]
                        ["snapshots" {:url "s3p://chartbeat-jars/snapshots/"
                                      :sign-releases false}]]

  ;; This says, when building jars, compile all the Lisp code
  ;; into Java jars (instead of passing it along to be interpreted at runtime.
  ;; You need this in order to run "lein uberjar".
  :aot :all

  ;; This tells leiningen we're using git.  It can't autodetect git if
  ;; the project isn't at the root of the
  ;; git repo.
  :vcs :git)
