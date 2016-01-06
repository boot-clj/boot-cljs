# boot-cljs [![Circle CI](https://circleci.com/gh/adzerk-oss/boot-cljs.svg?style=shield)](https://circleci.com/gh/adzerk-oss/boot-cljs) [![Downloads](https://jarkeeper.com/adzerk/boot-cljs/downloads.svg)](https://jarkeeper.com/adzerk/boot-cljs) [![Dependencies Status](https://jarkeeper.com/adzerk/boot-cljs/status.svg)](https://jarkeeper.com/adzerk/boot-cljs)

[](dependency)
```clojure
[adzerk/boot-cljs "1.7.170-3"] ;; latest release
```
[](/dependency)

[Boot](http://boot-clj.com/) task to compile ClojureScript applications.

* Provides the `cljs` task for compiling ClojureScript to JavaScript
* Provides a mechanism by which multiple multiple JS applications can be
  compiled in the same project.
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

For more [comprehesive guide](https://github.com/adzerk-oss/boot-cljs/wiki/Usage) check [wiki](https://github.com/adzerk-oss/boot-cljs/wiki).

### Further Reading

- [Simple example](https://github.com/adzerk-oss/boot-cljs/wiki/Example)
- [boot-cljs-example](https://github.com/adzerk/boot-cljs-example) - An example project with a local web server, CLJS REPL, and live-reload.
- [Saapas example project](https://github.com/Deraen/saapas) - Opionated example project for Boot.
- [Tenzing project template](https://github.com/martinklepsch/tenzing) - ClojureScript application template.
- [Modern ClojureScript](https://github.com/magomimmo/modern-cljs) - Series of tutorials for ClojureScript. Uses Boot.


## License

Copyright © 2014 Adzerk<br>
Copyright © 2015-2016 Juho Teperi

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
