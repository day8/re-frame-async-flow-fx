> Status:  still under development. Don't use yet.

## Async Control Flow In re-frame

Herein a re-frame effects handler, named `:async-flow`, 
which wrangles async tasks.

It is particularly useful for managing control flow at app boot time.

## Quick Start Guide
 
### Step 1. Add Dependency
 
Add the following project dependency:  
[![Clojars Project](http://clojars.org/re-frame-async-flow-fx/latest-version.svg)](http://clojars.org/re-frame-async-flow-fx)


### Step 2. Registration

In your root namespace, called perhaps `core.cljs`, in the `ns`...

```clj
(ns app.core
  (:require 
    ...
    [re-frame-async-flow-fx]   ;; <-- add this
    ...))
```

Because we never subsequently use this namespace, it 
appears redundant.  But the require will cause the `:async-flow` effect 
handler to self-register with re-frame, which is important
to everything that follows.


### Step 3. Initiate Boot

In your app's main entry function, we want to initiate the boot process:

```clj
(defn ^:export main
  []
  (dispatch-sync [:boot])            ;; <--- boot process is started
  (reagent/render 
    [this-app.views/main]                 
    (.getElementById js/document "app")))
```

Why the use of `dispatch-sync`, rather than `dispatch`?
 
Well, `dispatch-sync` is convenient here because it ensures that
`app-db` is synchronously initialised **before** we start mounting views (which subscribe to state).  Using
`dispatch` would work too, except it runs the handler **later**.  So, we'd have to then code 
defensively in our subscriptions and views, guarding against having an uninitialised `app-db`. 

This is the only known case where you should use `dispatch-sync` over `dispatch`. 

### Step 4. Event handler

Remember that `(dispatch-sync [:boot])` in step 3.  It is now time to write the event handler. 

In your event handlers namespace, perhaps called `events.cljs`...

**First**, create a data structure which defines the async flow required:
```clj
(def boot-flow 
  {:first-dispatch [:do-X]              ;; what event kicks things off ?
   :rules [
     {:when :seen? :events :success-X  :dispatch [:do-Y]}
     {:when :seen? :events :success-Y  :dispatch [:do-Z]}
     {:when :seen? :events :success-Z  :dispatch :halt}
     {:when :seen-any-of? :events [:fail-X :fail-Y :fail-Z] :dispatch  (list [:fail-boot] :halt)}])
```
You can almost read the `rules` as English sentences to understand what's being specified. Suffice 
it to say that tasks X, Y and Z will be run serially like dominoes. Much more complicated 
scenarios are possible.  But more on this data structure in the Tutorial (below).
 
**Second**, write the event handler for `:boot`:

This event handler will do two things:
  1. It goes though an initial synchronous series of tasks which get app-db into the right state
  2. It kicks off a multistep asynchronous flow

```clj
(def-event-fx                    ;; note the fx
  :boot                          ;; usage:  (dispatch [:boot])  See step 3
  (fn [_ _]
    {:db (-> {}                  ;;  do whatever synchronous work needs to be done
            task1-fn             ;; ?? set state to show "loading" twirly for user??
            task2-fn)            ;; ?? do some other simple initialising of state
     :async-flow  boot-flow}))   ;; kick off the async process
```

Look at that last line. This library defines the "effect handler" which implements `:async-flow`. It reads
and actions what's provided in `boot-flow`.

----

[![Clojars Project](https://img.shields.io/clojars/v/re-frame.svg)]

https://circleci.com/gh/:owner>/:repo.svg?style=shield&circle-token=:circle-ci-badge-token

[![GitHub license](https://img.shields.io/github/license/Day8/re-frame-async-flow-fx.svg)](license.txt)

Branch | Build Status
----------|--------
`master` | [![Circle CI](https://circleci.com/gh/Day8/re-frame-async-flow-fx/tree/master.svg?style=shield&circle-token=:circle-ci-badge-token)](https://circleci.com/gh/Day8/re-frame-async-flow-fx/tree/master)
`develop` | [![Circle CI](https://circleci.com/gh/Day8/re-frame-async-flow-fx/tree/develop.svg?style=shield&circle-token=:circle-ci-badge-token)](https://circleci.com/gh/Day8/re-frame-async-flow-fx/tree/develop)

---

## Tutorial

#### Problem Definition

When an App boots, it performs a set of tasks to initialise itself.

Invariably, there are dependencies between these tasks, like task1 has to run before task2.

Because of these dependencies, "something" has to coordinate how tasks are run.  Within the clojure community,
a library like [Stuart Sierra's Component](https://github.com/stuartsierra/component) or [mount](https://github.com/tolitius/mount)
is often turned to in these moments, but we won't be 
doing that here. We'll be using an approach which is more re-frame friendly.

#### Easy

If the tasks are all synchronous, then the coordination can be done in code. 
   
Each task is a function, and we satisfy the task dependencies by correctly 
ordering how they are called. In a re-frame context, we'd have this:   
```clj
(def-event
  :boot
  (fn [db]
    (-> {} 
        task1-fn 
        task2-fn
        task3-fn)))
```

and in our `main` function we'd `(dispatch [:boot])`


#### Time

But, of course, it is never that easy because some of the tasks will be asynchronous.  

A booting app will invariably have to coordinate **asynchronous tasks** like  
"open a websocket", "establish a database connections", "load from LocalStore",
"GET configuration from an S3 bucket" and "querying the database for the user profile".

**Coordinating asynchronous tasks means finding ways to represent and manage time**, 
and time is a programming menace.
In Greek mythology,  Cronus was the much feared Titan of Time, believed to
bring cruelty and tempestuous disorder, which surely makes him the patron saint of asynchronous programming.

Solutions like promises and futures attempt to make time disappear and allow you to program 
with the illusion of synchronous computation. But time has a tendency to act like a liquid under
pressure, finding the cracks and leaking through the abstractions.
      
Something like [CSP](https://en.wikipedia.org/wiki/Communicating_sequential_processes) (core.async) is 
more of an event oriented treatment. Less pretending. But... unfortunately more complicated. 
`core.async` builds a little state machine for you, under the covers, so that you can 
build your own state machine on 
top of that again via deft use of go loops, channels, gets and puts. Both layers try to model/control time. 

In our solution, we'll be using a re-frame variation which hides (most of) the 
state machine complexity.


#### Failures

There'll also be failures and errors!

Nothing messes up tight, elegant code quite like error 
handling.  Did the Ancient Greeks have a terrifying Titan for the unhappy
path too? They should have.
 
When one of the asynchronous startup tasks fails, we 
must be able to stop the normal boot sequence and put the application 
in a satisfactory failed state, explaining to the user
what went wrong (Eg: "No Internet connection" or "Couldn't load user portfolio").


#### Efficiency

And then there's the familiar pull of efficiency. 

We want our app to boot in the shortest possible amount of time. So any asynchronous 
tasks which could be done in parallel, should be done in parallel. 

So the boot process is seldom linear, one task after an another. Instead, it involves  
dependencies like:  when task1 has finished, we can start task2, task3 and task4 in 
parallel.  And task5 can be started only when both task2 and task3 has completed successfully. 
And task6 can start when task3 alone has completed, but we really don't care if it finishes 
properly - it is non essential to a working app.
 
So, we need to coordinate asynchronous flows, with complex dependencies, while handling failures.  
Not easy, but that's why they pay us the big bucks.

#### As Data Please

Because we program in Clojure, we spend time in hammocks carefully re-watching Rich Hickey videos and
meditating on essential truths like "data is the ultimate in late binding".

So, our solution must involve "programming with data", and be, at once, all synonyms of easy. 

#### In One Place 

The control flow should be described in just one place, and easily 
grokable as a unit.

To put that another way: we do not want the logic implemented in a way that would  
require a programmer to look in multiple places  to  reconstruct 
a mental model of the overall control flow.
 


## The Solution

re-frame has events. That's how we roll.

A re-frame application can't step forward in time unless an event happens; unless something
does a `dispatch`.  Events will be the organising principle in our solution exactly
because events are an organising principle within re-frame itself.

### Tasks and Events

Our solution assumes the following about tasks...  

If we take an X-ray of an async task, we'll see this event skeleton: 
 - an event is used to start the task
 - if the task succeeds, an event is dispatched
 - if the task fails, an event is dispatched
 
So that's three events: one to start and two ways to finish.

Of course, re-frame will route all three events to a registered handler. The actual WORK of 
starting the task, or handling the errors, will be done in the event handler that you write. 

But, here, none of that menial labour concerns us. We care only about the **coordination** of 
tasks. We care only that task2 is started when task1 finishes successfully, and we don't need 
to know what task1 or task2 actually do.  
 
To distill that: we care only that the `dispatch` to start task2 is fired correctly when we
have seen an event saying that task1 finished successfully. 
 
### When-E1-Then-E2
  
Read that last paragraph again.  It distills even further to: when event E1 happens then `dispatch` event E2.  Or, simply, When-E1-Then-E2. 
  
When-E1-Then-E2 is the simple case, with more complicated variations like:
  - when **both** events E1 and E2 have happened, then dispatch E3
  - when **either** events E1 or E2 happens, then dispatch E3
  - when event E1 happens, then dispatch both E2 and E3
  
We call these "rules". A collection of such rules defines a "flow". 

### Flow As Data

Collectively, a set of When-E1-then-E2 rules can describe the entire async boot flow of an app.   

Here's how that might look in data:
```clj
[{:when :seen?        :events :success-db-connect   :dispatch (list [:do-query-user] [:do-query-site-prefs])}
 {:when :seen-all-of? :events [:success-user-query :success-site-prefs-query]   :dispatch (list [:success-boot] :halt)}
 {:when :seen-any-of? :events [:fail-user-query :fail-site-prefs-query :fail-db-connect] :dispatch  (list [:fail-boot] :halt)}
 {:when :seen?        :events :success-user-query   :dispatch [:do-intercom]}]
```

That's a vector of 4 maps (one per line), where each represents a single rule. Try to read each 
line as if it was an English sentence and something like this should emerge: `when we have seen all of events E1 and E2, then dispatch this other event`

The structure of each rule (map) is: 
```clj
{:when     X      ;; one of:  :seen?, :seen-all-of?, :seen-any-off? 
 :events   Y      ;; either a single keyword or a seq of keywords representing event ids
 :dispatch Z}     ;; either a single vector (to dispatch) or a list of vectors (to dispatch). :halt is special
```

We can't issue a database query until we have a database connection, so the 1st rule (above) says:
  1. When `:success-db-connect` is dispatched, presumably signalling that we have a database connection...
  2. then `(dispatch [:query-user])`  and `(dispatch [:query-site-prefs])`
  
If both database queries succeed, then we have successfully booted, and the boot process is over. So, the 2nd rule says:
  1. When both success events have been seen (they may arrive in any order),  
  2. then `(dispatch [:success-queries])` and cleanup because the boot process is `:halt`.
  
If any task fails, then the boot fails, and the app can't start. So go into a failure mode. And 
the boot process has done. So the 3rd rules says:
  1.  If any one of the various tasks fail...
  2.  then `(dispatch [:fail-boot])` and cleanup because the boot process is `:halt`. 
 
When we have user data (from the user-query), we can start the intercom process. So the 4th rules days:
  1. When `:success-user-query` is dispatched 
  2. then  `(dispatch [:do-intercom])`
  
Further Notes:

1. The 4th rule starts "Intercom" once we have completed the user query. But notice that 
   nowhere do we wait for a `:success-intercom`.  We want this process started, 
   but it is not essential for the app's function, so we don't wait for it to complete.
    
2. The coordination processes never actively participates in handling any events. Event handlers 
   themselves do all that work. They know how to handle success or failure - what state to record so 
   that the twirly thing is shown to users, or not. What messages are shown. Etc.

3. A naming convention for events is adopted. Each task can have 3 associated events which
   are named as follows: `:do-*` is for starting tasks. Task completion is either `:success-*`
   or `:fail-*`

4. The special `:dispatch` value of `:halt` means the boot flow is completed.  Clean up the flow coordinator. 
   It will have some state somewhere. So get rid of that.  And it will have been "sniffing events", 
   so stop doing that too.
   
5. There's nothing in here about the teardown process as the application is closing. Here's we're only
   helping the boot process. 

6. There will need to be something that kicks off the whole flow. In the case above, presumably 
   a `(dispatch [:do-connect-db])` is how it all starts.

7. A word on Retries XXXX

### The Flow Specification 

The async-flow data structure has the following fields:

  - `:id` - optional - an identifier, typically a namespaced keyword. Expected to be unique. 
    Must not clash with the identifier for any event handler (because internally 
    an event handler is registered using this id).  
    If absent, `:async/flow` is used.  
    If this default is used then two flows can't be running at once because they'd be using the same id.   
    
  - `db-path` - optional - the path within `app-db` where the coordination logic should store state. Two pieces
     of state are stored:  the set of seen events, and the set of started tasks.
    If absent, then state is not stored in app-db and is instead held in an internal atom. 
    We prefer to store state in app-db because we like the philosophy of having all the data in the one place, 
    but it is not essential. 
  - `first-dispatch` - mandatory - the event which initiates the async flow. This is often 
    something like the event which will open a websocket or HTTP GET configuration from the server.
  - `rules` - mandatory - a vector of maps. Each map is a `rule`.
  
A `rule` has the following 3 fields:

  - `:when`  one of `:seen?`, `:seen-all-of?`, `:seen-any-of?`
    `:seen?` and `:seen-all-of?` are the same.
  - `:events` either a single keyword, or a seq of keywords, presumably event ids
  - `:dispatch` can be a single vector representing one event to dispatch, 
     or a list of vectors representing multiple events to `dispatch`  
     The special value `:halt` can be supplied in place of an event vector, and it 
     means to teardown the flow. 


### Under The Covers 

How exactly does async-flow work? 

It does the following:
  1. It creates an event handler to perform the flow coordination.  
  2. It registers this event handler using the supplied `:id` 
  3. It requests that all `:events` mentioned in `flow` rules should be "forwarded" to 
     this event handler, after they have been handled by their normal handlers.
     So, if the event `[:abc 1]` was part of `flow` spec, then after `[:abc 1]` was handled by its normal handler
     there would be an additional `(dispatch [:async/flow  [:abc 1]])` which would be handled the coordinator 
     created in steps 1 and 2.  
  4. the event handler keeps track of what events have occurred, and what tasks have already 
     been started. It keeps this state at the path nominated in `:db-path`. 
  5. the event handler uses your `flow` specification and the state it maintains to work out how it should 
     respond to each arriving event. 
  6. At some point, the flow finishes (failed or succeeded) and the event handler from 1 
     is de-registered, and event sniffing is stopped. 


Notes:
  1.  This pattern is very flexible. It could be modified to handle more complex FSM.
  2.  All the work is done in a normal event handler (created for you).  And it is all organised around events. 
      So this solution is based on pure re-frame fundamentals. 
    

### Design Philosophy

Managing async task flow means managing time, and managing time requires a state machine. You need:
   - some retained state   (describing where we have got to) 
   - events which announce that something has happened or not happened (aka triggers)
   - a set of rules about transitioning state  (a function of existing state, the new trigger) 
   
One way or another you'll be implementing a Finite State Machine.
   
Redux-saga uses ES6 generator functions to provide the illusion of a  
synchronous control flow. The "state machine" is encoded directly into the generator function's 
statements (one after the other, or with if, or with loops). So that's a nice 
and simple solution for many cases. 

But there are trade-offs. 

First, it does indeed mean encoding the state machine in "code" (and letting the generator function be 
the state machine). In clojure, we have a preference for "programming in data".  

Second, coding (in javascript) a more complicated state machines with a bunch of failure states and 
cascades will ultimately get messy. Time is like a liquid under pressure and it will force it way 
out through the cracks in the abstraction.  History tells us to implement state machines 
in a table driven way (data driven way). 

So we choose data.

But it would be quite possible to create a re-frame version of redux-saga.  In ClosureScript
we have core.async instead of generator functions. That is left as an exercise for the enthusiastic reader.

  
