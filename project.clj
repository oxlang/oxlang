(defproject org.oxlang/oxlang "0.1.0-SNAPSHOT"
  :description "牛, the language"
  :url "http://github.com/oxlang/oxlang"
  :license {:name "MIX/X11 license"
            :url "http://opensource.org/licenses/MIT"}
  :whitelist #"oxlang.*"
  :plugins [[lein-cloverage "1.0.2"]]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/test.check "0.5.9"]
                 [clj-antlr "0.2.2"]])
