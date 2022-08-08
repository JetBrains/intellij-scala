[![official JetBrains project](http://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![Scala Plugin Build & Test](https://github.com/JetBrains/intellij-scala/actions/workflows/build.yml/badge.svg)](https://github.com/JetBrains/intellij-scala/actions/workflows/build.yml)
[![Discord](https://badgen.net/badge/icon/discord?icon=discord&label)](https://discord.gg/aUKpZzeHCK)

# Scala Plugin for IntelliJ IDEA

The plugin adds support for the Scala language:
 - Coding assistance (highlighting, completion, formatting, refactorings, etc.)
 - Navigation, search, information about types and implicits
 - Integration with sbt and other build tools
 - Testing frameworks support (ScalaTest, Specs2, uTest)
 - Scala debugger, worksheets and Ammonite scripts

(note that HOCON support was moved to a [separate plugin](https://plugins.jetbrains.com/plugin/10481-hocon))

## General information

- To get information about how to install and use this plugin in IDEA, please
  use [IntelliJ IDEA online help](https://www.jetbrains.com/idea/help/scala.html)

- If you have any question about the Scala plugin, we'd be glad to answer it in [our discord channel](https://discord.gg/aUKpZzeHCK) or in [our
  developer community](https://devnet.jetbrains.com/community/idea/scala) 

- If you found a bug, please report it on [our issue
  tracker](https://youtrack.jetbrains.com/issues/SCL#newissue)

- If you want to contribute, please see our [intro to the Scala plugin
  internals](https://blog.jetbrains.com/scala/2016/04/21/how-to-contribute-to-intellij-scala-plugin/).

## Setting up the project

### Prerequisites
In order to take part in Scala plugin development, you need:

1. IntelliJ IDEA 2021.3 or higher with a compatible version of Scala plugin
2. JDK 11
3. (optional but **recommended**) \
   Enable [internal mode](https://plugins.jetbrains.com/docs/intellij/enabling-internal.html) in IDEA to get access to helpful internal actions and debug information

### Setup

1. Clone this repository to your computer

  ```
  $ git clone https://github.com/JetBrains/intellij-scala.git
  ```

2. Open IntelliJ IDEA, select `File -> New -> Project from existing sources`, point to
the directory where the Scala plugin repository is and then import it as sbt project.

3. In the next step, select JDK 11 as project JDK (create it from an installed JDK if necessary).

5. Select the `scalaCommunity` run configuration and select the `Run` or `Debug` button to build and start a
development version of IDEA with the Scala plugin.


## [IntelliJ Platform SDK documentation](https://plugins.jetbrains.com/docs/intellij/welcome.html)

## Browsing IntelliJ Platform sources

When loading Scala Plugin project in sbt, the IntelliJ platform is downloaded to `<home>/.ScalaPluginIC/sdk/<sdk version>/`. 
IntelliJ platform sources should be automatically attached after project has been imported and indices have been built.

However, sometimes this doesn't happen and the sources are not attached. As a result you see decompiled code when opening a Platform API class.
To fix this you can invoke "Attach Intellij Sources" action (you need to enable [internal mode](https://plugins.jetbrains.com/docs/intellij/enabling-internal.html) to access this action)

## Tests

To run tests properly, the plugin needs to be packaged.
On the sbt shell:

1. `packageArtifact`
2. `runFastTests`

The "fast tests" can take over an hour. To get a quick feedback on project health, run only the type inference tests:

    > runTypeInferenceTests

## Generating test coverage reports

You might want to generate a test coverage report for a given package. It can be done by running for example the following:
```
sbt "project scala-impl;set coverageEnabled := true;project scalaCommunity;testOnly org.jetbrains.plugins.scala.codeInspection.unusedInspections.*;scala-impl/coverageReport"
```
Close to the very tail of the output of this command you will find a line that gives you the location of the generated report, for example:
```
[info] Written HTML coverage report [/Users/alice/intellij-scala/scala/scala-impl/target/scala-2.13/scoverage-report/index.html]
```
Note that in order to continue working from IntelliJ IDEA again you need to perform Build > Rebuild Project.

### [Docs for writing tests for IntelliJ plugins](https://plugins.jetbrains.com/docs/intellij/testing-plugins.html) 

## GitHub Actions build

The project is configured to build and run the typeInference tests and fast tests with Github Actions. The full test suite isn't run to avoid really long build times.

## Running the plugin

### Debugging mode

The easiest way to try your changes is typically to launch the `scalaCommunity` run configuration which is created
when you set up the project as described above.

### As a standalone plugin

To run and distribute a modified version of the plugin in a regular IntelliJ instance, you need to package it.

1. on the sbt shell, run `packageArtifactZip`. This will output the generated plugin zip location
   (typically into `<project directory>/target/scala-plugin.zip`).
2. In IntelliJ, open Preferences, section Plugins, choose "Install plugin from disk..." and navigate to the scala-plugin.zip
3. Restart IntelliJ

## Other
### Investigation performance issues
- YourKit
- There is a "Scala plugin profiler" tool window to track invocations of methods with `@Cached*` or `@Measure` annotations (from `org.jetbrains.plugins.scala.macroAnnotations` package) in real time. The tool window is available in [internal mode](https://plugins.jetbrains.com/docs/intellij/enabling-internal.html) or if `-Dinternal.profiler.tracing=true` is passed to IDEA using [custom VM options](https://www.jetbrains.com/help/idea/tuning-the-ide.html#procedure-jvm-options)