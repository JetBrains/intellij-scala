# worksheet-repl-interface

A standalone sbt project for defining and implementing the Scala Worksheet REPL interface.

The subprojects in this sbt project used to directly belong to the build of the Scala Plugin for IntelliJ IDEA repository.

The Scala Plugin needs to be built using JDK 21 since the 2025.3 release of IntelliJ IDEA. Because of this, these subprojects
can no longer live in the Scala Plugin repository, as they need to be compiled with old versions of Scala which do not
run on Java 21.

## Development notes

The root `worksheet-repl-interface` directory can be opened as a standalone IntelliJ IDEA project and developed
completely independently.

The project requires exactly JDK 17 and no other JDK version is accepted.

Similarly, starting `sbt` in this directory will fail if using any JDK other than JDK 17.

Here's how to start `sbt` with a non-default JDK:
```
sbt --java-home <path to JDK 17 installation directory>
```

Use the command `sbt cleanAll` to clean all compilation and packaged outputs.

## Publishing notes

```shell
sbt package
```
to publish the two product jars. The output jars are:
  1. `repl-interface/target/repl-interface.jar`
  2. `impls/target/impls.jar`

We include these jars as is in the following two directories in the Scala Plugin repo:
  1. `repl-interface.jar` &#x2192; `<community root>/scala/worksheet/repl-interface/lib/repl-interface.jar`
  2. `impls.jar` &#x2192; `<community root>/scala/worksheet/repl-interface/impls/lib/impls.jar`

You need to manually put the newly produced jars in these expected directories and replace the old jars.

If making any changes to the interface or implementations, consider incrementing the version strings. They might be useful
for historical reference. This version is reflected in the MANIFEST file of the resulting jars.

## Project details

The REPL interface is defined in pure Java and can be found in the `repl-interface` subproject.

The implementations for different Scala versions can be found under the `impls` directory.
