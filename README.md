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

## License

Copyright © 2014 Micha Niskin and Alan Dipert

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[1]: https://github.com/tailrecursion/boot
[2]: http://clojars.org/tailrecursion/boot-cljs/latest-version.svg
[3]: http://clojars.org/tailrecursion/boot-cljs
