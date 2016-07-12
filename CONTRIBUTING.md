# Contributing to re-frame-async-flow-fx

Thank you for taking the time to contribute!

## Support questions

The Github issues are for bug reports and feature requests. Support requests and usage 
questions should go to the re-frame [Clojure Slack channel](http://clojurians.net) or
the [ClojureScript mailing list](https://groups.google.com/forum/#!forum/clojurescript).


## Pull requests

**Create pull requests to the develop branch**, work will be merged onto master when it is ready to be released.

## Running the tests

To run the tests, you must have recent versions of node, npm, Leiningen, and a C++ compiler toolchain installed. 
If you're on Linux or Mac OS X then you will be fine, if you're on Windows then you need to install 
Visual Studio Community Edition, and the C++ compiler dependencies.

```sh
lein deps    # runs lein-npm, installs Karma & other node dependencies. Only needed the first time.
lein once    # or lein auto # to build re-frame-async-flow-fx
karma start  # to run the tests with an auto watcher
```

## Pull requests for bugs

If possible provide:

* Code that fixes the bug
* Failing tests which pass with the new changes
* Improvements to documentation to make it less likely that others will run into issues (if relevant).
* Add the change to the Unreleased section of [CHANGES.md](CHANGES.md)

## Pull requests for features

If possible provide:

* Code that implements the new feature
* Tests to cover the new feature including all of the code paths
* Docstrings for functions
* Documentation examples
* Add the change to the Unreleased section of [CHANGES.md](CHANGES.md)
