EventDebugger (Only listens for changes)

This is just a test plugin I use when I need to, feel free to use it although it might have a negative effect on production servers if kept running (lower tps due to event calls etc.)

If one of your million plugins are behaving badly, this could be useful to find out which exact plugin is changing a result or cancelling a method.

This uses [ReflectASM](https://github.com/EsotericSoftware/reflectasm), which supposedly should allow it to have better performance when debugging than pure reflection.

Default config, change accordingly

```yaml
PickupEvent:
  class: "org.bukkit.event.entity.EntityPickupItemEvent" # Use the proper class
  methods: # Only getters without parameters
    - "isCancelled"
InteractEvent:
  class: "org.bukkit.event.player.PlayerInteractEvent"
  methods:
    - "isCancelled"
```