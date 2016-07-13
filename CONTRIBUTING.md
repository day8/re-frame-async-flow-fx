# Contributing to re-frame-async-flow-fx

Thank you for taking the time to contribute!

## Support questions

The Github issues are for bug reports and feature requests. Support requests and usage
questions should go to the re-frame [Clojure Slack channel](http://clojurians.net) or
the [ClojureScript mailing list](https://groups.google.com/forum/#!forum/clojurescript).


## Pull requests

**Create pull requests to the develop branch**, work will be merged onto master when it is ready to be released.

## Running the tests

#### Via Browser/HTML
```sh
lein test-once  # builds re-frame-async-flow-fx tests & opens browser on test/test.html
                # or lein test-auto # then open a browser on test/test.html
                # and refresh browser to rerun tests after each auto compile.
```

#### Via Karma

To run the tests, you must have recent versions of node, npm, Leiningen, and a C++ compiler toolchain installed.
If you're on Linux or Mac OS X then you will be fine, if you're on Windows then you need to install
Visual Studio Community Edition, and the C++ compiler dependencies.

```sh
lein deps       # runs lein-npm, installs Karma & other node dependencies. Only needed the first time.
lein karma-once # to build re-frame-async-flow-fx tests
karma start     # to run the tests with an auto watcher
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
