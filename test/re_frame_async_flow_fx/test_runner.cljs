(ns re-frame-async-flow-fx.test-runner
  (:require [jx.reporter.karma :as karma :include-macros true]
            [devtools.core :as devtools]
            [re-frame-async-flow-fx.core-test]))

(devtools/install! [:custom-formatters :sanity-hints]) ;; we love https://github.com/binaryage/cljs-devtools
(enable-console-print!)

;;TODO stub for tests.

(defn ^:export run [karma]
  (karma/run-tests
    karma
    're-frame-async-flow-fx.core-test))
