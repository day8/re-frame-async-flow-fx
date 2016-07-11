
Branch | Build Status
----------|--------
`master` | [![Circle CI](https://circleci.com/gh/Day8/re-frame-async-flow-fx/tree/master.svg?style=svg)](https://circleci.com/gh/Day8/re-frame-async-flow-fx/tree/master)
`develop` | [![Circle CI](https://circleci.com/gh/Day8/re-frame-async-flow-fx/tree/develop.svg?style=svg)](https://circleci.com/gh/Day8/re-frame-async-flow-fx/tree/develop)


### Async Control Flow In re-frame
 
When an App boots, it performs a series of tasks to initialise itself.

Invariably, there are dependencies between these tasks, like task1 has to run before task2. Because of 
these dependencies, "something" has to coordinate how tasks are run. "something" has to 
provide the control flow.

This library provides a re-frame friendly way to manage control flow for a clowder of async tasks. The 
library is presented in terms of it managing the boot control flow but, actually, 
it can be used anytime you need to wrangle multiple async tasks. 

----

### Three Step How To 

A tutorial follows, but here's the basic steps...    
 
##### 1. Project Dependency
 
`re-frame-async-flow-fx` is available from clojars. Use the following project dependencies:
[![Clojars Project](http://clojars.org/re-frame-async-flow-fx/latest-version.svg)](http://clojars.org/re-frame-async-flow-fx)


##### 2. Dispatch :boot

In your app's main entry function, `dispatch`the `:boot` event. This puts 
some basic data into app-db, and kicks off the necessary async flow.

```cljs
(defn ^:export main
  []
  (dispatch-sync [:boot])                   ;; boot process is started
  (reagent/render [this-app.views/main]     ;; mount the main UI view 
                  (.getElementById js/document "app")))
```


##### 3. Event handler
 
In your event handler namespace, called perhaps `events.cljs`...

**3a.** At the top, require as follows: 
```cljs
(require 
   ...
   [re-frame-async-flow-fx :as async-flow-fx]
   ...)
```
Although apparently unnecessary because we never use the namespace, we need to require it
to ensure the effect handler (used in step 3c) is registered.  

**3b.** define the async flow required   

```
(def boot-async-flow 
  {:id             :my-flow                                   ;; a unique id
   :db-path        [:path :to :store :flow :state :within :db]   
   :first-dispatch [:do-X]                                    ;; what event kicks things off ?
   :rules [{:when :seen-all-of :events #{:success-X}   :dispatch [:do-Y]}
           {:when :seen-all-of :events #{:success-Y }  :dispatch [:do-Z]}
           {:when :seen-all-of :events #{:success-Z }  :dispatch :done}
           {:when :seen-any-of :events #{:fail-X :fail-Y :fail-Z} :dispatch  (list [:fail-boot] :done)}])
```
More on this format in the tutorial below.

**3c.** write the event handler:

```
(def-event-fx                         ;; note the fx
  :boot                               ;; usage:  (dispatch [:boot])  See step 2
  (fn [_]
    {:db (-> {}                       ;;  do whatever synchronous work needs to be done
            task1-fn                  ;; ?? set state to show "loading" twirly for user??
            task2-fn)                 ;; ?? do some other simple initialising of state
     :async-flow  boot-async-flow}))  ;; kick off the async process
```

Look at that last line. This library defines the "effect handler" which interprets that effect. It reads and actions 
the specification supplied in `boot-async-flow`.  

Just to be clear, this event handler does two things:
  1. It goes though an initial synchronous series of tasks which get app-db into the right state. 
  2. It kicks off a multistep asynchronous flow. Described in data via `boot-async-flow`.

----
### Tutorial

#### Problem Definition

When an App boots, it performs a set of tasks to initialise itself.

Invariably, there are dependencies between these tasks, like task1 has to run before task2.

Because of these dependencies, something has to coordinate how tasks are run.  Within the clojure community,
a library like [Stuart Sierra's Component](https://github.com/stuartsierra/component) or [mount](https://github.com/tolitius/mount)
is often turned to in these moments, but we won't be 
doing that here. We'll be using an approach which is more re-frame friendly.

#### Easy

If the tasks are all synchronous, then the coordination can be done in code. 
   
Each task is a function, and we satisfy the task dependencies by correctly 
ordering how they are called.

Within a re-frame context, we'd have this:   
```
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

A booting app will invariably have to 
coordinate **asynchronous tasks** like  "open a websocket", "establish a database connections", 
"load from LocalStore",
"GET configuration from an S3 bucket" and "querying the database for the user profile".

**Coordinating asynchronous tasks means finding ways to represent and manage time***, 
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
dependencies like:  when task task1 has finished, we can start task2, task3 and task4 in 
parallel.  But task5 can't start until both task2 and task3 has completed successfully. 
And task6 can start when task3 is done, but we really don't care if it finishes 
properly - it is non essential to a working app.
 
So, we need to coordinate asynchronous timelines, with complex dependencies, while handling failures.  
Not easy, but that's why they pay us the big bucks.

#### As Data Please

Because we program in Clojure, we spend time in hammocks lazily re-watching Rich Hickey videos and
meditating on essential truths like "data is the ultimate in late binding".

Our solution should involve "programming with data" and be, at once, all synonyms of easy. 

#### In One Place 

The control flow should be described in just one place, and easily 
grokable as a unit.

To put that another way: we do not want the logic implemented in a way that would  
require a programmer to look in multiple places  to  reconstruct 
a mental model of the overall control flow.
 
---

## The Solution

re-frame has events. That's how we roll.

A re-frame application can't step forward in time, unless an event happens; unless something
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
  
We call these "rules". A collection of rules defines a "flow". 

### Flow As Data

Collectively, a set of these When-E1-then-E2 rules can describe the entire async boot flow of an app.   

Here's how we might describe rules in data:
```
[{:when :seen-all-of :events #{:success-db-connect}   :dispatch (list [:do-query-user] [:do-query-site-prefs])}
 {:when :seen-all-of :events #{:success-user-query :success-site-prefs-query}   :dispatch (list [:success-boot] :done)}
 {:when :seen-any-of :events #{:fail-user-query :fail-site-prefs-query :fail-db-connect} :dispatch  (list [:fail-boot] :done)}
 {:when :seen-all-of :events #{:success-user-query}   :dispatch [:do-intercom]}]
```

That's a vector of 4 maps (one per line), where each represents a single rule. Try to read each 
line as if it was an English sentence and something like this should emerge: `when we have seen all of events E1 and E2, then dispatch this other event`

The structure of each rule (map) is: 
```
{:when     X      ;; one of:   :seen-all-of  or :seen-any-off
 :events   Y      ;; a set of one or more event ids
 :dispatch Z}     ;; either a single vector (to dispatch) or a list of vectors (to dispatch). :done is special
```

We can't issue a database query until we have a database connection, so the 1st rule (above) says:
  1. When `:success-db-connect` is dispatched, presumably signalling that we have a database connection...
  2. then `(dispatch [:query-user])`  and `(dispatch [:query-site-prefs])`
  
If both database queries succeed, then we have successfully booted, and the boot process is over. So, the 2nd rule says:
  1. When both success events have been seen (they may arrive in any order),  
  2. then `(dispatch [:success-queries])` and cleanup because the boot process is `:done`.
  
If any task fails, then the boot fails, and the app can't start. So go into a failure mode. And the boot process has done. So the 3rd rules says:
  1.  If any one of the various tasks fail...
  2.  then `(dispatch [:fail-boot])` and cleanup because the boot process is `:done`. 
 
When we have user data (from the user-query), we can start the intercom process. So the 4th rules days:
  1. When `:success-user-query` is dispatched 
  2. then  `(dispatch [:do-intercom])`
  
Further Notes:

1. The 4th rule starts "intercom" once we have completed the user query. But notice that 
   nowhere do we wait for a `:success-intercom`.  We want this process started, 
   but it is not essential for the app's function, so we don't wait for it to complete.
    
2. The coordination processes never actively participates in handling any events. Event handlers 
   themselves do all that work. They know how to handle success or failure - what state to record so 
   that the twirly thing is shown to users, or not. What messages are shown. Etc.

3. A naming convention for events is adopted. Each task can have 3 associated events which
   are named as follows: `:do-*` is for starting tasks. Task completion is either `:success-*`
   or `:fail-*`

4. A dispatch of `:done` means the boot process is completed.  Clean up the coordinator. 
   It will have some state somewhere. So get rid of that.  And it will be "sniffing events"
   
5. There's nothing in here about the teardown process at the end of the application.  We're only
   helping the startup process. 

6. To start the boot process  `(dispatch [:do-connect-db])` 

7. A word on Retries XXXX


### The Code


Using `dispatch-sync` is convenient because it ensures that
`app-db` is correctly initialised before we start mounting views (which subscribe to state).  Using   
`dispatch` would work too, except it runs the handler "later".  So, we'd have to then code 
defensively in our subscriptions and views, guarding against having an uninitialised `app-db`. 


**First** create the full `boot-flow` spec.   Above, I gave just the `:rules` part of the spec. 

```
(def boot-async-flow 
  {:id       :my-flow
   :db-path  [:place :to :store :state :within :db]     ;; the coordinator needs to keep some state. Where?
   :first-dispatch [:do-db-connection]                  ;; how does the process get kicked off?
   :rules [{:when :seen-all-of :events #{:success-db-connect}   :dispatch (list [:do-query-user] [:do-query-site-prefs])}
           {:when :seen-all-of :events #{:success-user-query :success-site-prefs-query}   :dispatch (list [:success-boot] :done)}
           {:when :seen-any-of :events #{:fail-user-query :fail-site-prefs-query :fail-db-connect} :dispatch  (list [:fail-boot] :done)}
           {:when :seen-all-of :events #{:success-user-query}   :dispatch [:do-intercom]}])
```

**Second**, change the initialisation handler. It should:
  1. do the synchronous (easy) stuff to get app-db in a good state
  2. kicking off the async tasks "flow"

```
(register-event-fx
  :initialise
  (fn [_]
    {:db (-> {} task1-fn task2-fn)       ;;  do whatever synchronous work needs to be done
     :async-flow  boot-async-flow}))           ;; kick off the async process
```

That's it.  

The real work is done by the effects handler for `async-flow`.  It takes the flow you provide and makes it all happen. 

### Async-Flow 

This effects handler does the following:
  1. It creates an event handler to perform the coordination. 
  2. It registers this event handler using the `id` you supplied in `boot-async-flow` which was `:my-flow`
  3. It organises that all events mentioned in `boot-async-flow` rules are be "forward" to this event handler, 
     after they have been handled by their normal handlers.
     So, let's say the event `[:abc]` was part of `boot-flow`. After `[:abc]` was handled by its normal handler
     there would be an additional `(dispatch [:my-flow  [:abc]])`.  In effect the event `[:abc]` is 
     dispatched as a 1st parameter to the coordinating handler registered in step 2. 
  4. the event handler uses your `boot-flow` spec to do the flow coordination
  5. At some point, the boot has finished (failed or succeeded) and the event handler from 1 
     is de-registered, and events sniffing stops.


Notes
1.  This pattern is very flexible. It could be modified to handle more complex FSM.
2.  All the work is done in a normal event handler (created for you).  And it is all organised around events. 
    So we're playing to the  basic features in re-frame.
