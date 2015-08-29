## 1.7.48-3 (24.8.2015)

- **BREAKING**: Changed the way `:ids` option selects the files. Instead of looking only at the basename of file, it looks at the paths. E.g. to use `src/js/main.cljs.edn` use `--ids js/main` instead of just `--ids main`.
- Fixed advanced optimization caused by generated main namespace not being on
classpath
- Fixed where advanced optimization was broken with multiple builds
[#92](https://github.com/adzerk-oss/boot-cljs/pull/92).

## 1.7.48-2 (20.8.2015)

- Display reader exceptions in pretty format
- Fix [#89](https://github.com/adzerk-oss/boot-cljs/issues/89).

## 1.7.48-1 (20.8.2015)

- Fix Cljs version assertion
- Add Cljs output files as resources so they'll be available in classpath

## 1.7.48-0 (20.8.2015)

- Support reloading macro namespaces
- Use ClojureScript API
- Builds specified by `.cljs.edn` files are now run parallel in separated
environments.
- Add `:ids` option to select used `.cljs.edn` files
- Bug fixes

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
