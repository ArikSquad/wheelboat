# Wheelboat

A client-side Fabric Loader mod for Minecraft Java Edition 26.1.2 that controls
vanilla boats with a physical USB steering wheel and pedals.

The mod reads joystick axes through GLFW and converts them to Minecraft's vanilla
boat controls. Servers may detect this as a cheat. 

This mod has been tested with a G923 under Linux. Windows wheel input is
supported through GLFW and Windows force feedback is implemented through
DirectInput.

Force feedback can vary by the material under the boat. The Cloth Config screen
exposes spring multiplier and roughness values for water, ice, sand, dirt,
stone, wood, and a default fallback.

## Build

Requires Java 25 or newer.

```sh
./gradlew build
```

The distributable mod jar is written to `build/libs/`.
