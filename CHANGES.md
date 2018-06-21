## Unreleased

#### NEW
   - Add :dispatch-fn to the rule specification. [#20](https://github.com/Day8/re-frame-async-flow-fx/pull/20) see ["Advanced use"](https://github.com/Day8/re-frame-async-flow-fx#advanced-use) in readme
   - Make :first-dispatch optional [#12](https://github.com/Day8/re-frame-async-flow-fx/issues/12)

## v0.0.7  (2017.07.09)
   - remove :halt-flow dispatch

## v0.0.5  (2016.07.XX)
    - allow flow handler to be used from non fx handler via flow->handler
    - rules can either use :dispatch for one or :dispatch-n for multiple

## v0.0.2  (2016.07.XX)

#### clean ups and corrections
    - def-fx name typo -> :async-flow
    - cleanup unit test namespaces
    - fix def-fx to use default-id
    - fix bug async-flow-handler
    - rename effect :event-forwarder -> :forward-events to match [forward-events-fx](https://github.com/Day8/re-frame-forward-events-fx) i.e. :forward-events
    - remove special dispatch `:halt-flow` and introduce new optional rule property `:halt? true`
    - rename state :started-tasks -> :rules-fired
    - readme updates

## v0.0.1  (2016.07.XX)

    - initial code drop
