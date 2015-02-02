# boot-cljs

[](dependency)
```clojure
[adzerk/boot-cljs "0.0-2760-0"] ;; latest release
```
[](/dependency)

[Boot] task to compile ClojureScript applications.

* Provides the `cljs` task–compiles ClojureScript to JavaScript.

* Provides a mechanism by which multiple multiple JS applications can be
  compiled in the same project.

* Provides a mechanism by which JS preamble, and Google Closure libs and
  externs files can be included in Maven jar dependencies or from the project
  source directories.

## Try It

In a terminal do:

```bash
mkdir src
echo -e '(ns foop)\n(.log js/console "hello world")' > src/foop.cljs
boot -s src -d adzerk/boot-cljs cljs
```

The compiled JavaScript will be written to `target/main.js`.

## Usage

Add `boot-cljs` to your `build.boot` dependencies and `require` the namespace:

```clj
(set-env! :dependencies '[[adzerk/boot-cljs "X.Y.Z" :scope "test"]])
(require '[adzerk.boot-cljs :refer :all])
```

You can see the options available on the command line:

```bash
boot cljs -h
```

or in the REPL:

```clj
boot.user=> (doc cljs)
```

You can see debug level output from the task by setting boot's logging level:

```bash
boot -vv cljs
```

This will show the options being passed to the CLJS compiler.

### Compilation Levels

The ClojureScript compiler uses the Google Closure compiler to generate
optimized JavaScript when desired. There are [three different Closure
compilation levels][closure-levels]: `whitespace`, `simple`, and
`advanced`. You may specify the desired compilation level with the `-O`
or `--optimizations` option:

```bash
boot cljs -O advanced
```

The default level is `none`, which bypasses the Closure compiler completely.
This is handy for development work because it's very fast.

### Same HTML for Development and Production

The HTML file will always need to have a `<script>` tag to load the compiled
JavaScript:

```html
<!-- compiling with optimizations != none -->
<script type='text/javascript' src='main.js'></script>
```

Since the Closure compiler concatenates the individual JS files this is all
you need for the production version of your application.

For development, however, the Closure compiler is bypassed and no concatenation
is done, so you normally need to add two other `<script>` tags for the Closure
`base.js` dependency and to `goog.require()` your main namespace, plus tags to
load any external JS libraries your application depends on:

```html
<!-- external JS libraries -->
<script type='text/javascript' src='react.min.js'></script>
<script type='text/javascript' src='jquery.min.js'></script>

<!-- compiling with optimizations == none -->
<script type='text/javascript' src='out/goog/base.js'></script>
<script type='text/javascript' src='main.js'></script>
<script type='text/javascript'>goog.require('my.namespace');</script>
```

**This is not necessary with the boot `cljs` task.** The `cljs` task is smart
enough to arrange all of this for you automatically, so the application HTML
file can be the same whether compiling with or without optimizations. You only
need the one `<script>` tag as in the first example above. This will work with
all compilation levels.

### Source Maps

[Source maps][src-maps] associate locations in the compiled JavaScript file with
the corresponding line and column in the ClojureScript source files. When source
maps are enabled (the `-s` or `--source-map` option) the browser developer tools
will refer to locations in the ClojureScript source rather than the compiled
JavaScript.

```bash
boot cljs -s
```

> You may need to enable source maps in your browser's developer tools settings.

### Compiler Options

The `cljs` task normally does a good job of figuring out which options to pass
to the compiler on its own. However, options can be provided via the `-c` or
`--compiler-options` option, to provide [specific options to the CLJS compiler][cljs-opts]:

```bash
boot cljs -c '{:target :nodejs}'
```

### Incremental Builds

You can run boot such that it watches source files for changes and recompiles
the JavaScript file as necessary:

```bash
boot watch cljs
```

You can also get audible notifications whenever the project is rebuilt:

```bash
boot watch speak cljs
```

> **Note:** The `watch` and `speak` tasks are not part of `boot-cljs`–they're
> built-in tasks that come with boot.

### Multiple Builds

The `cljs` task provides a way to specify application entry points at which it
can point the CLJS compiler for compilation. These entry points are provided
via files with the `.cljs.edn` extension.

These files have the following structure (eg. `js/index.cljs.edn`):

```clojure
{:require  [foo.bar baz.baf]
 :init-fns [foo.bar/init baz.baf/doit]
 :compiler-options {:target :nodejs}}
```

For each `.cljs.edn` file in the fileset, the `cljs` task will:

* Create a CLJS namespace corresponding to the file's path, eg. given the file
  `foo/bar.cljs.edn` it will create the `foo.bar` CLJS namespace. This namespace
  will `:require` any namespaces given in the `:require` key of the EDN, and
  add a `do` expression that calls any functions in `:init-fns` at the top
  level. These functions will be called with no arguments.

* Configure compiler options according to `:compiler-options` key of the EDN,
  if there is one.

* Configure the compiler to produce compiled JS at a location derived from the
  file's path, eg. given the file `foo/bar.cljs.edn` the output JS file will
  be `foo/bar.js`.

* Point the CLJS compiler at the generated namespace only. This "scopes" the
  compiler to that namespace plus any transitive dependencies via `:require`.

The example above would result in the following CLJS namespace, `js/index.cljs`:

```clojure
(ns js.index
  (:require foo.bar baz.baf))

(do (foo.bar/init)
    (baz.baf/doit))
```

and would be compiled to `js/index.js`. This is the JS script you'd add to the
application's HTML file via a `<script>` tag.

### Browser REPL

See the [adzerk/boot-cljs-repl][boot-cljs-repl] boot task.

### Preamble, Externs, and Lib Files

Jars with `deps.cljs`, like the ones provided by [cljsjs][cljsjs] can
be used to supply Javascript libraries. Alternatively the mechanism
described below can be used:

The `cljs` task scans the fileset for files that have special filename
extensions. File extensions recognized by the `cljs` task:

* `.inc.js`: JavaScript preamble files–these are prepended to the compiled
  Javascript in dependency order (i.e. if jar B depends on jar A then entries
  from A will be added to the JavaScript file such that they'll be evaluated
  before entries from B).

* `.lib.js`: GClosure lib files (JavaScript source compatible with the Google
  Closure compiler).

* `.ext.js`: [GClosure externs files][closure-externs]–hints to the Closure
  compiler that prevent it from mangling external names under advanced
  optimizations.

## Examples

Create a new ClojureScript project, like so:

```
my-project
├── build.boot
├── html
│   └── index.html
└── src
    └── foop.cljs
```

and add the following contents to `build.boot`:

```clj
(set-env!
  :src-paths    #{"src"}
  :rsc-paths    #{"html"}
  :dependencies '[[adzerk/boot-cljs "0.0-X-Y" :scope "test"]])

(require '[adzerk.boot-cljs :refer :all])
```

Then in a terminal:

```bash
boot cljs -s
```

The compiled JavaScript file will be `target/main.js`.

### Preamble and Externs

Add preamble and extern files to the project, like so:

```
my-project
├── build.boot
├── html
│   └── index.html
└── src
    ├── foop.cljs
    └── js
        ├── barp.ext.js
        └── barp.inc.js
```

With the contents of `barp.inc.js` (a preamble file):

```javascript
(function() {
  window.Barp = {
    bazz: function(x) {
      return x + 1;
    }
  };
})();
```

and `barp.ext.js` (the externs file for `barp.inc.js`):

```javascript
var Barp = {};
Barp.bazz = function() {};
```

Then, in `foop.cljs` you may freely use `Barp`, like so:

```clj
(ns foop)

(.log js/console "Barp.bazz(1) ==" (.bazz js/Barp 1))
```

Compile with advanced optimizations and source maps:

```bash
boot cljs -sO advanced
```

You will see the preamble inserted at the top of `main.js`, and the references
to `Barp.bazz()` are not mangled by the Closure compiler. Whew!

### Further Reading

For an example project with a local web server, CLJS REPL, and live-reload,
check out [boot-cljs-example]!

## License

Copyright © 2014 Adzerk

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[Boot]:                https://github.com/boot-clj/boot
[boot-clojars-latest]: http://clojars.org/adzerk/boot-cljs/latest-version.svg?cache=6
[boot-clojars]:        http://clojars.org/adzerk/boot-cljs
[cider]:               https://github.com/clojure-emacs/cider
[boot-cljs-repl]:      https://github.com/adzerk/boot-cljs-repl
[src-maps]:            https://developer.chrome.com/devtools/docs/javascript-debugging#source-maps
[closure-compiler]:    https://developers.google.com/closure/compiler/
[closure-levels]:      https://developers.google.com/closure/compiler/docs/compilation_levels
[closure-externs]:     https://developers.google.com/closure/compiler/docs/api-tutorial3#externs
[boot-cljs-example]:   https://github.com/adzerk/boot-cljs-example
[cljs-opts]:           https://github.com/clojure/clojurescript/wiki/Compiler-Options
[cljsjs]:              https://github.com/cljsjs/packages
