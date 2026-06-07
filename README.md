# Wheelboat

A client-side Fabric Loader mod for Minecraft Java Edition 26.1.2 that controls
vanilla boats with a physical USB steering wheel and pedals.

The mod reads joystick axes through GLFW and converts them to Minecraft's vanilla
boat controls. Servers may detect this as a cheat. 

This mod has only been tested with a G923 and under Linux. 
It might work under Windows if the force feedback stuff don't cause it to break.

## Build

Requires Java 25 or newer.

```sh
./gradlew build
```

The distributable mod jar is written to `build/libs/`.
