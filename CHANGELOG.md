# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.4.0] - 2023-05-23
### Changed
- :debug? now also logs :first-dispatch. Now also documented see README.md `Debugging`
- bump provided dependencies including clojure 1.11.1, clojurescript 1.11.60 and shadow-cljs 2./23.3
  lein-ancient replaced with com.github.liquidz/antq
- merge PR #38 with doc fixes. Thanks @romdog

## [0.3.0] - 2021-06-03
### Changed
- Update deps to including latest clojure, clojurescript & shadow-cljs

### Added
- Debugging specific flows via an optional `:debug?` parameter. 
  When `true`, the setup, halt and dispatches triggered by rules are logged via `re-frame.console` with the flow id
  to help differentiate console entries with multiple flows running. 
  Each console entry includes a map with:
  ```
  :id         - the configured or assigned flow id.
  :ts         - a high resolution time via `cljs.core/system-time`.
  :signal     - the trigger, this can be the initial :setup or the event(s)  causing a rule to be matched.
  :dispatched - the event(s) being fired by the matched rule. Only present on dispatch log.
  ```
- Debugging can also be globally turned on/of via `(async-flow-fx/enable-debug? true)`
- Flow rules get scanned for at least one :halt?  if none a warning is logged.

## [0.2.0] - 2021-03-04
### Added
- Merged PR #34 `::async-flow-fx/notify` NOP event handler for minimal flow signals. See ["Event Messaging" in README.md](https://github.com/day8/re-frame-async-flow-fx#event-messaging)
### Changed
- upgrade clojure to 1.10.2
- upgrade shadow-cljs to 2.11.18
- upgrade to re-frame 1.2.0
