# Comsat Dropwizard+jOOQ Example

A single-file Dropwizard embedded Java 1.7+ featuring fiber-blocking JAX-RS web services backed by fiber-blocking jOOQ DB access.

## Getting started

Just edit `gradle/user-props.gradle`. You might want to edit JVM arguments and system properties in `gradle/dropwizard.gradle`, then feel free to play with the code or 
just give it a try as it stands.

To run it:

```
./gradlew run # CTRL+C to stop
```

The available services are printed by Dropwizard on stdout during bootstrap.
