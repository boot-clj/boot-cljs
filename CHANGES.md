## Unreleased

- Stop following ClojureScript compiler version number
  - Boot-cljs uses the [public compiler API](https://github.com/clojure/clojurescript/blob/master/src/main/clojure/cljs/build/api.clj)
  - The API is static and should not change between versions
  - ClojureScript compiler versions since [1.7.28](https://github.com/clojure/clojurescript/blob/master/changes.md#1728)
which included change [CLJS-1367](https://github.com/clojure/clojurescript/commit/9e662c3ea5b9f536f303e9084415e988bf5d0878), should work.
- Expose ClojureScript compiler options in `.cljs.edn` Boot fileset metadata for
others tasks (e.g. boot-reload)
- Hide stacktraces for Cljs compiler exceptions (reader exception, analysis error)

## 1.7.228-2 (18.10.2016)

**[compare](https://github.com/adzerk-oss/boot-cljs/compare/1.7.228-1...1.7.228-2)**

- Changed the order compiler-options are merged. Task-options are now merged on top of
options from .cljs.edn. This allows overriding options per build task invocation.

## 1.7.228-1 (11.1.2016)

**[compare](https://github.com/adzerk-oss/boot-cljs/compare/1.7.228-0...1.7.228-1)**

- Fixed optimization none builds ([#115](https://github.com/adzerk-oss/boot-cljs/pull/115))

## 1.7.228-0 (10.1.2016)

**[compare](https://github.com/adzerk-oss/boot-cljs/compare/1.7.170-3...1.7.228-0)**

- Update default ClojureScript release to 1.7.228
- Improved exception messages in some cases. ([#110](https://github.com/adzerk-oss/boot-cljs/issues/110))
- Added *experimental* support for Closure Modules using `:modules` option on
`.cljs.edn` files. ([#113](https://github.com/adzerk-oss/boot-cljs/issues/113))

## 1.7.170-3 (8.11.2015)

**[compare](https://github.com/adzerk-oss/boot-cljs/compare/1.7.170-2...1.7.170-3)**

- Fixed critical bug introduced in the previous release
    - Second compilation broke the main shim

## 1.7.170-2 (8.11.2015)

**[compare](https://github.com/adzerk-oss/boot-cljs/compare/1.7.170-1...1.7.170-2)**

- Read `.cljs.edn` always instead of only once
    - This allows editing `:require` and `:compiler-options` in without
    restarting the build process.

## 1.7.170-1 (6.11.2015)

**[compare](https://github.com/adzerk-oss/boot-cljs/compare/1.7.166-1...1.7.170-1)**

- Update to match latest ClojureScript release

## 1.7.166-1 (4.11.2015)

- Removed workaround related to recompile-dependents which is made unncessary
by [CLJS-1437](https://github.com/clojure/clojurescript/commit/409d1eca4fcf776be1f7b28f759d6f36f7a83ec8).
- Fixed source-map options when `optimizations` is not explicitly set.

## 1.7.48-6 (16.10.2015)

- Fixed problem where default main (no `.cljs.edn` files) tried to compile
`deps.cljs` files in the fileset.

## 1.7.48-5 (4.10.2015)

- Warning messages now display file path without tmp-dir path
- Adds information about warnings and exceptions to fileset so that
[boot-reload](https://github.com/adzerk-oss/boot-reload) can use the
information to display HUD.
- Cljs exceptions are now always rethrown, this is to allow other tasks to
catch the exceptions and to see when build failed

## 1.7.48-4 (19.9.2015)

- Improved ClojureScript dependency check logic
    1. If ClojureScript dependency is specific in build.boot, use that
    2. If no dependency is found but ClojureScript is available in classpath,
    display a warning
    3. If no dependency is found and ClojureScript is not available, add dependency

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
