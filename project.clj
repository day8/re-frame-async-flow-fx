(defproject    day8.re-frame/async-flow-fx "see :git-version below https://github.com/arrdem/lein-git-version"
  :description "A re-frame effects handler for coordinating the kind of async control flow which often happens on app startup."
  :url         "https://github.com/day8/re-frame-async-flow-fx.git"
  :license     {:name "MIT"}

  :git-version
  {:status-to-version
   (fn [{:keys [tag version branch ahead ahead? dirty?] :as git}]
     (assert (re-find #"\d+\.\d+\.\d+" tag)
       "Tag is assumed to be a raw SemVer version")
     (if (and tag (not ahead?) (not dirty?))
       tag
       (let [[_ prefix patch] (re-find #"(\d+\.\d+)\.(\d+)" tag)
             patch            (Long/parseLong patch)
             patch+           (inc patch)]
         (format "%s.%d-%s-SNAPSHOT" prefix patch+ ahead))))}

  :dependencies [[org.clojure/clojure             "1.10.1" :scope "provided"]
                 [org.clojure/clojurescript       "1.10.520" :scope "provided"
                  :exclusions [com.google.javascript/closure-compiler-unshaded
                               org.clojure/google-closure-library]]
                 [thheller/shadow-cljs            "2.8.69" :scope "provided"]
                 [re-frame                        "0.10.9" :scope "provided"]
                 [day8.re-frame/forward-events-fx "0.0.6"]]

  :plugins [[me.arrdem/lein-git-version "2.0.3"]
            [lein-shadow                "0.1.5"]]

  :profiles {:debug {:debug true}
             :dev   {:dependencies [[karma-reporter     "3.1.0"]
                                    [day8.re-frame/test "0.1.5"]
                                    [binaryage/devtools "0.9.10"]]
                     :plugins      [[lein-ancient "0.6.15"]
                                    [lein-shell   "0.5.0"]]}}

  :clean-targets [:target-path
                  "resources/public/js/test"
                  "shadow-cljs.edn"
                  "package.json"
                  "package-lock.json"]

  :resource-paths ["run/resources"]
  :jvm-opts ["-Xmx1g"]
  :source-paths ["src"]
  :test-paths ["test"]

  :shadow-cljs {:builds {:browser-test
                         {:target    :browser-test
                          :ns-regexp "-test$"
                          :test-dir  "resources/public/js/test"
                          :devtools  {:http-root "resources/public/js/test"
                                      :http-port 8290}}

                         :karma-test
                         {:target    :karma
                          :ns-regexp "-test$"
                          :output-to "target/karma-test.js"}}}

  :shell {:commands {"open" {:windows ["cmd" "/c" "start"]
                             :macosx  "open"
                             :linux   "xdg-open"}}}

  :deploy-repositories [["clojars" {:sign-releases false
                                    :url           "https://clojars.org/repo"
                                    :username      :env/CLOJARS_USERNAME
                                    :password      :env/CLOJARS_PASSWORD}]]

  :release-tasks [["deploy" "clojars"]]

  :aliases {"dev-auto"   ["do"
                          ["clean"]
                          ["shadow" "watch" "browser-test"]]
            "karma-once" ["do"
                          ["clean"]
                          ["shadow" "compile" "karma-test"]
                          ["shell" "karma" "start" "--single-run" "--reporters" "junit,dots"]]})
