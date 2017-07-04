# `.cljs.edn` files

`.cljs.edn` files are configuration files for Boot-cljs which allow options
being set in a separate file instead of in `build.boot` task call.

There are several benefits to providing configuration in separate files:

Using separate file is that the options can be changed
without restarting the whole Boot process.

It is possible to calculate some options based on the
relative path of `.cljs.edn` file inside the fileset. If user doesn't
provide `:output-dir` or `:output-to` options, the values are set up using
relative path of the `.cljs.edn` file.

Perhaps the most important feature of `.cljs.edn` files is that other tasks
can modify this configuration. For example Boot-reload and Boot-cljs-repl
use this set up their code using `:require`.

Boot-cljs is able to run several Cljs compiler parallel if multiple
`.cljs.edn` files are present.

> NOTE: This might change.
> Cljs compiler can now work parallel itself, if `:parallel-build` option is enabled.
> For cases there the application should be split to several JS files, `:modules`
> is probably better fit as that way the shared code is not duplicated to every
> output file.
> For cases where separate builds, like app and tests, are needed, it might be
> simpler to just run two cljs tasks.

## `:compiler-options`

[Compiler options](./compiler-options.md) can be provided here.

## `:require`

Collection of namespaces to require in Boot-cljs generated main namespace.

## `:init-fns`

Collection of functions (namespaced symbols) to call from Boot-cljs main namespace,
called after all the `:require` namespaces are loaded.
Namespaces of these symbols are automatically added to `:require`.
