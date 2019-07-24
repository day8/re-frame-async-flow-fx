(defproject day8.re-frame/async-flow-fx "0.1.1-SNAPSHOT"
  :description  "A re-frame effects handler for coordinating the kind of async control flow which often happens on app startup."
  :url          "https://github.com/Day8/re-frame-async-flow-fx.git"
  :license      {:name "MIT"}
  :dependencies [[org.clojure/clojure        "1.10.1"  :scope "provided"]
                 [org.clojure/clojurescript  "1.10.520" :scope "provided"]
                 [re-frame                   "0.10.8"  :scope "provided"]
                 [day8.re-frame/forward-events-fx "0.0.6"]]

  :profiles {:debug {:debug true}
             :dev   {:dependencies [[karma-reporter "3.1.0"]
                                    [day8.re-frame/test "0.1.5"]
                                    [binaryage/devtools "0.9.10"]]
                     :plugins      [[lein-ancient "0.6.15"]
                                    [lein-cljsbuild "1.1.7"]
                                    [lein-npm "0.6.2"]
                                    [lein-shell "0.5.0"]]}}

  :clean-targets  [:target-path "run/compiled"]

  :resource-paths ["run/resources"]
  :jvm-opts       ["-Xmx1g"]
  :source-paths   ["src"]
  :test-paths     ["test"]

  :shell          {:commands {"open" {:windows ["cmd" "/c" "start"]
                                      :macosx  "open"
                                      :linux   "xdg-open"}}}

  ;; > lein deploy
  :deploy-repositories [["clojars"  {:sign-releases false
                                     :url "https://clojars.org/repo"
                                     :username :env/CLOJARS_USERNAME
                                     :password :env/CLOJARS_PASSWORD}]]

  ;; > lein release
  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "v" "--no-sign"]
                  ["deploy" "clojars"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]

  :npm {:devDependencies [[karma                 "4.1.0"]
                          [karma-cljs-test       "0.1.0"]
                          [karma-chrome-launcher "2.2.0"]
                          [karma-junit-reporter  "1.2.0"]]}

  :cljsbuild {:builds [{:id           "test"
                        :source-paths ["test" "src"]
                        :compiler     {:preloads        [devtools.preload]
                                       :external-config {:devtools/config {:features-to-install :all}}
                                       :output-to     "run/compiled/browser/test.js"
                                       :source-map    true
                                       :output-dir    "run/compiled/browser/test"
                                       :optimizations :none
                                       :source-map-timestamp true
                                       :pretty-print  true}}
                       {:id           "karma"
                        :source-paths ["test" "src"]
                        :compiler     {:output-to     "run/compiled/karma/test.js"
                                       :source-map    "run/compiled/karma/test.js.map"
                                       :output-dir    "run/compiled/karma/test"
                                       :optimizations :whitespace
                                       :main          "day8.re_frame.async_flow_fx_test_runner"
                                       :pretty-print  true}}]}

  :aliases {"test-once"   ["do" "clean," "cljsbuild" "once" "test," "shell" "open" "test/test.html"]
            "test-auto"   ["do" "clean," "cljsbuild" "auto" "test,"]
            "karma-once"  ["do" "clean," "cljsbuild" "once" "karma,"]})
