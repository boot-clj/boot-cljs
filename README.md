# boot-cljs [![Clojars Project](https://img.shields.io/clojars/v/adzerk/boot-cljs.svg)](https://clojars.org/adzerk/boot-cljs) [![Circle CI](https://circleci.com/gh/boot-clj/boot-cljs.svg?style=shield)](https://circleci.com/gh/boot-clj/boot-cljs) [![Downloads](https://jarkeeper.com/adzerk/boot-cljs/downloads.svg)](https://jarkeeper.com/adzerk/boot-cljs)

[Boot](http://boot-clj.com/) task to compile ClojureScript applications.

* Provides the `cljs` task for compiling ClojureScript to JavaScript
* Requirements
    * Supports [ClojureScript versions](https://github.com/clojure/clojurescript/blob/master/changes.md) since 1.7.28
    * Boot version 2.6.0 but 2.7.0 is recommended for better error reporting
    * Java 8+
* Docs
    * [.cljs.edn files](docs/cljs.edn.md)
    * [Compiler options](docs/compiler-options.md), Boot-cljs modified and automatically sets some options
* **Related projects:** [boot-reload](https://github.com/adzerk-oss/boot-reload) and [boot-cljs-repl](https://github.com/adzerk-oss/boot-cljs-repl)

## Quick start

Add ClojureScript and `boot-cljs` to your `build.boot` dependencies and `require` the namespace:

```clj
(set-env! :dependencies '[[adzerk/boot-cljs "X.Y.Z" :scope "test"]])
(require '[adzerk.boot-cljs :refer [cljs]])
```

You can see the options available on the command line:

```bash
boot cljs --help
```

Or the same in the REPL:

```clj
boot.user=> (doc cljs)
```

### Further Reading

- [boot-cljs-example](https://github.com/adzerk/boot-cljs-example) - An example project with a local web server, CLJS REPL, and live-reload.
- [Saapas example project](https://github.com/Deraen/saapas) - Opinionated example project for Boot.
- [Tenzing project template](https://github.com/martinklepsch/tenzing) - ClojureScript application template.
- [Modern ClojureScript](https://github.com/magomimmo/modern-cljs) - Series of tutorials for ClojureScript. Uses Boot.


## License

Copyright © 2014 Adzerk<br>
Copyright © 2015-2017 Juho Teperi

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
