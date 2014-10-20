# boot-cljs

[![Clojars Project][2]][3]

Boot task to compile ClojureScript applications.

* Provides the `cljs` task–compiles ClojureScript to JavaScript.
* Provides a mechanism by which JS preamble, and Google Closure libs and externs
  files can be included in Maven jar dependencies or from the project source
  directories.

## Usage

Add `boot-cljs` to your `build.boot` dependencies and `require` the namespace:

```clj
(set-env! :dependencies '[[tailrecursion/boot-cljs "X.Y.Z" :scope "test"]])
(require '[tailrecursion.boot-cljs :refer :all])
```

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

## Preamble and GClosure Extern/Lib Files

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

## License

Copyright © 2014 Micha Niskin and Alan Dipert

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[1]: https://github.com/tailrecursion/boot
[2]: http://clojars.org/tailrecursion/boot-cljs/latest-version.svg
[3]: http://clojars.org/tailrecursion/boot-cljs
