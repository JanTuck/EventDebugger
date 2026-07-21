# EventDebugger

EventDebugger identifies the exact plugin listener that changes a Bukkit event. It snapshots the
configured values before and after each listener and prints only real changes.

## Quick start

1. Put the shaded jar in the server's `plugins` directory and restart the server.
2. Run `/eventdebugger status` to verify the configured event subscriptions.
3. Reproduce the problem and look for a concise change in the console:

```text
PlayerInteractEvent changed by ExamplePlugin (HIGH, com.example.InteractListener)
  isCancelled: false -> true
```

Edit `plugins/EventDebugger/config.yml` to select event classes and zero-argument methods, then run
`/eventdebugger reload`. You do not need to restart the server.

Run `/eventdebugger gui` in-game for the visual dashboard. Current Paper versions use the native
dialog UI when available; Spigot and older Paper versions automatically receive the chest UI. The
chest dashboard provides live event activity, detailed before/after history, plugin filters,
subscription toggles, and a guided chat flow for adding new event classes and getters.

The modern UI includes clickable live traces, stable numbered outcomes, inline before/after diffs,
native event-subscription forms, and a Capture Settings form for debugger state, history capacity,
and listener discovery cadence. Dialog actions report their result inside the current screen.

## Commands

| Command | Purpose |
| --- | --- |
| `/eventdebugger status` | Show state, subscriptions, listener count, and counters |
| `/eventdebugger gui` | Open the best visual dashboard available on this server |
| `/eventdebugger events` | Show live activity for subscribed events |
| `/eventdebugger history` | Review recorded listener outcomes |
| `/eventdebugger filters` | Choose which installed plugins are inspected |
| `/eventdebugger subscriptions` | List configured exact subscriptions |
| `/eventdebugger refresh` | Immediately discover newly registered listeners |
| `/eventdebugger reload` | Reload configuration and safely re-instrument listeners |
| `/eventdebugger on` / `off` | Persistently enable or disable instrumentation |

The aliases `/edebug` and `/ed` are also available. Commands require `eventdebugger.admin`, which
defaults to server operators.

## Performance controls

Exact subscriptions are the fastest option. `listen-to-all-cancellable` is deliberately disabled
by default because it scans and observes many event types. Use `include-plugins` when you only need
to investigate one or two plugins, and increase `refresh-interval-ticks` if plugins on your server
never register listeners dynamically.

EventDebugger caches all reflected methods, skips already-instrumented listeners during refreshes,
and creates only one value snapshot array per inspected listener call. Disabling it restores the
original Bukkit executors immediately.

The production jar targets Java 8 bytecode and compiles only against Spigot API 1.16.1. It links no
Paper API classes; the embedded dialog framework discovers modern capabilities reflectively and is
optional. Kotlin,
ReflectASM, Reflections, and SLF4J are shaded and relocated inside the EventDebugger namespace.
User-configured events are resolved from the running server and installed plugin class loaders, so
they can be newer than the API used to compile EventDebugger.
