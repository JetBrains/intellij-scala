# Scala Worksheet REPL interface

## repl-interface

The module `repl-interface` contains compiled definitions of the Scala Worksheet REPL interface classes. The classes
are distributed in the `lib/repl-interface.jar` jar file as an unmanaged library dependency.

The source files of the interface can be found in the standalone `<community root>/scala/worksheet-repl-interface`
project in the `repl-interface` subproject.

## impls

Additionally, the implementations of the Scala Worksheet REPL interface for different Scala versions can be found
in the `impls` subdirectory. Again, the implementation classes are distributed in a precompiled jar file in
`impls/lib/impls.jar` as an unmanaged library dependency.

The source files of the implementation classes can be found in the same project in the `impls` subproject.

## Developer notes

Consult the `README.md` of the `<community root>/scala/worksheet-repl-interface` project for more information
on how to change and publish the interface and implementation classes.
