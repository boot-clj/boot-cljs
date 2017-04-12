# Integration with other tasks

## `.cljs.edn`

Other tasks can modify the Cljs build options by modifying `.cljs.edn` files
in the fileset. For example boot-reload and boot-cljs-repl add their
client namespaces to `:require` vector in `.cljs.edn` files.

Examples:

- https://github.com/adzerk-oss/boot-reload/blob/master/src/adzerk/boot_reload.clj#L84-L89
- https://github.com/adzerk-oss/boot-reload/blob/master/src/adzerk/boot_reload.clj#L138-L148

## Without `.cljs.edn` files

FIXME:

If tasks don't find `.cljs.edn` files, they don't currently have way to
define which namespaces to include in build. This works for now,
as Boot-cljs will create default `.cljs.edn` file, which has `:require`
in any `.cljs` files in fileset. However, this causes the main file to
require all the namespaces, even those that are unused.

## Related

- [Boot-reload HUD messages](https://github.com/adzerk-oss/boot-reload/blob/master/doc/hud-messages.md)
