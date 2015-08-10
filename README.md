# boot-cljs

```clojure
[adzerk/boot-cljs "0.0-3308-0"] ;; latest stable release
```

[](dependency)
```clojure
[adzerk/boot-cljs "1.7.48-SNAPSHOT"] ;; latest release
```
[](/dependency)

[Boot] task to compile ClojureScript applications.

* Provides the `cljs` task–compiles ClojureScript to JavaScript.

* Provides a mechanism by which multiple multiple JS applications can be
  compiled in the same project.

## Try It

In a terminal do:

```bash
mkdir src
echo -e '(ns foop)\n(.log js/console "hello world")' > src/foop.cljs
boot -s src -d adzerk/boot-cljs cljs
```

The compiled JavaScript will be written to `target/main.js`.

## Usage

Add ClojureScript and `boot-cljs` to your `build.boot` dependencies and `require` the namespace:

```clj
(set-env! :dependencies '[[adzerk/boot-cljs "X.Y.Z" :scope "test"]])
(require '[adzerk.boot-cljs :refer :all])
```

You can see the options available on the command line:

```bash
boot cljs -h
```

Or in the REPL:

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
<script type='text/javascript' src='main.js'></script>
```

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

### Options


| Option | Description | Task option | `.cljs.edn` |
| ---    | ---         | --- | --- |
| `optimization` | `:none` (default), `:advanced` | × | |
| `source-map` | Use source maps (default true for `:none` optimization) | × | |
| `id` | Selected `.cljs.edn` file | × | |
| `require` | Namespaces to require on load | | × |
| `init-fns` | Functions to call on load | | × |
| `compiler-options` | Cljs compiler options | × | × |


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

### Preamble and Externs Files

Jars with `deps.cljs`, like the ones provided by [cljsjs][cljsjs] can
be used to supply Javascript libraries. If you need to use local js files
you can manually create deps.cljs in your local project:

src/deps.cljs:
```clj
{:foreign-libs [{:file "bar.js"
                 :provides ["foo.bar"]}]
 :externs ["bar.ext.js"]}
```

src/bar.js:
```js
function foo() {
  console.log("Hello world from local js");
}
```

src/bar.ext.js:
```js
foo = function() {};
```

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

And add the following contents to `build.boot`:

```clj
(set-env!
  :source-paths    #{"src"}
  :resource-paths    #{"html"}
  :dependencies '[[adzerk/boot-cljs "0.0-X-Y" :scope "test"]])

(require '[adzerk.boot-cljs :refer :all])
```

Then in a terminal:

```bash
boot cljs -s
```

The compiled JavaScript file will be `target/main.js`.

Compile with advanced optimizations and source maps:

```bash
boot cljs -sO advanced
```

### Further Reading

For an example project with a local web server, CLJS REPL, and live-reload,
check out [boot-cljs-example]!

## License

Copyright © 2014 Adzerk<br>
Copyright © 2015 Juho Teperi

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
