# boot-cljs

[![Clojars Project][2]][3]

Boot task to compile ClojureScript applications.

* Provides the `cljs` task–compiles ClojureScript to JavaScript.
* Provides a mechanism by which JS preamble, and Google Closure libs and externs
  files can be included in Maven jar dependencies or from the project source
  directories.

## Try It

In a terminal do:

```bash
echo -e '(ns foop)\n(.log js/console "hello world")' > foop.cljs
boot -d tailrecursion/boot-cljs cljs
```

The compiled JavaScript will be written to `main.js`.

## Usage

Add `boot-cljs` to your `build.boot` dependencies and `require` the namespace:

```clj
(set-env! :dependencies '[[tailrecursion/boot-cljs "X.Y.Z" :scope "test"]])
(require '[tailrecursion.boot-cljs :refer :all])
```

You can see the options available on the command line:

```bash
boot cljs -h
```

or in the REPL:

```clj
boot.user=> (doc cljs)
```

## Preamble, Extern, and Lib Files

The `cljs` task figures out what to do with these files by scanning for
resources on the classpath that have special filename extensions. They should
be under `hoplon/include/` in the source directory or jar file.

File extensions recognized by the `cljs` task:

* `.inc.js`: JavaScript preamble files–these are prepended to the compiled
  Javascript in dependency order (i.e. if jar B depends on jar A then entries
  from A will be added to the JavaScript file such that they'll be evaluated
  before entries from B).
* `.lib.js`: GClosure lib files (JavaScript source compatible with the Google
  Closure compiler).
* `.ext.js`: GClosure externs files–hints to the Closure compiler that prevent
  it from mangling external names under advanced optimizations.

## Example

Create a new ClojureScript project, like so:

```
my-project
├── build.boot
└── src
    └── foop.cljs
```

and add the following contents to `build.boot`:

```clj
(set-env!
  :src-paths    #{"src"}
  :dependencies '[[tailrecursion/boot-cljs "0.0-2371-4" :scope "test"]])

(require '[tailrecursion.boot-cljs :refer :all])
```

Then in a terminal:

```bash
boot cljs
```

The compiled JavaScript file will be `target/main.js`.

### With Preamble and Externs

Add preamble and extern files to the project, like so:

```
my-project
├── build.boot
└── src
    ├── foop.cljs
    └── hoplon
        └── include
            ├── barp.ext.js
            └── barp.inc.js
```

Where the contents of `barp.inc.js` are:

```javascript
(function() {
  window.Barp = {
    bazz: function(x) {
      return x + 1;
    }
  };
})();
```

and `barp.ext.js` are:

```javascript
var Barp = {};
Barp.bazz = function() {};
```

Then, in `foop.cljs` you may freely use `Barp`, like so:

```clj
(ns foop)

(.log js/console "Barp.bazz(1) ==" (.bazz js/Barp 1))
```

Compile with advanced optimizations:

```bash
boot cljs -O advanced
```

You will see the preamble inserted at the top of `main.js`, and the references
to `Barp.bazz()` are not mangled by the Closure compiler. Whew!

### Incremental Builds

You can run boot such that it watches `:src-paths` for changes to source files
and recompiles the JavaScript file as necessary. This works best with `:none`
optimizations, because that's the fastest way to compile.

```bash
boot watch cljs -O none
```

You can also get audible notifications whenever the project is rebuilt:

```bash
boot watch speak cljs -O none
```

> **Note:** The `watch` and `speak` tasks are not part of `boot-cljs`–they're
> built-in tasks that come with boot.

### ClojureScript REPL

You can also obtain a REPL in the browser (or in Nashorn et al) where you may
evaluate ClojureScript expressions. Here is a sample `build.boot` file to get
you started:

```clj
(set-env!
  :src-paths    #{"src"}
  :rsc-paths    #{"rsc"}
  :dependencies '[[tailrecursion/boot-cljs   "0.0-2371-11" :scope "test"]
                  ;;; cljs repl dependencies follow:
                  [org.clojure/clojurescript "0.0-2371"    :scope "test"]
                  [com.cemerick/piggieback   "0.1.3"       :scope "test"]
                  [weasel                    "0.4.1"       :scope "test"]])

(require
  '[tailrecursion.boot-cljs :refer :all]
  '[weasel.repl.websocket   :refer [repl-env]]
  '[cemerick.piggieback     :refer [cljs-repl wrap-cljs-repl]])

(task-options!
  repl [:middleware [#'wrap-cljs-repl]])
```

This gets the build environment set up with the project dependencies you'll
need. Then you need to connect to the REPL server from the ClojureScript client:

```clj
(ns foop
  (:require [weasel.repl :as repl]))

(if-not (repl/alive?)
  (repl/connect "ws://localhost:9001"))

(.log js/console "hello world")
```

Finally, fire up a REPL and build the project:

```bash
boot repl -s watch cljs -O none
```

This starts a REPL server and incremental compilation of ClojureScript. Then
fire up a REPL client (in emacs via [cider], perhaps) and do:

```clj
boot.user=> (cljs-repl :repl-env (repl-env))
```

Boom.

## License

Copyright © 2014 Micha Niskin and Alan Dipert

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[1]: https://github.com/tailrecursion/boot
[2]: http://clojars.org/tailrecursion/boot-cljs/latest-version.svg?cache=2
[3]: http://clojars.org/tailrecursion/boot-cljs
[cider]: https://github.com/clojure-emacs/cider
