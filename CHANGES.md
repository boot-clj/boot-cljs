## 1.7.48 (x.y.2015)

- Support reloading macro namespaces
- Use ClojureScript API

## 0.0-3308-0 (13.6.2015)

- Updated to latest ClojureScript version

## 0.0-3269-4 (13.6.2015)

- Instead automatically adding or updating Clojure 1.7 dependency,
display a warning and link to [Boot wiki](https://github.com/boot-clj/boot/wiki/Setting-Clojure-version)
page about setting the Clojure version.

## 0.0-3269-3 (13.6.2015)

- Broken release

## 0.0-3269-2 (30.5.2015)

- Path->js fix fir cljc namespaces

## 0.0-3269-1 (23.5.2015)

- Automatically add Clojure 1.7 dependency to Cljs pod if project
is not yet using it.

## 0.0-3269-0 (17.5.2015)

- **Probably breaks stuff**: Updated to latest ClojureScript compiler
- **Might break stuff**: Uses shim created by cljs compiler
- **Breaking**: Removed \*.inc.js, \*.ext.js, \*.lib.js handling
  - Most of use cases are covered by [cljsjs](http://cljsjs.github.io/)
  - If you still need to add local js files to the build, you can add deps.cljs
  file to your local project
