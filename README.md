[![official JetBrains project](http://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![Scala Plugin Build & Test](https://github.com/JetBrains/intellij-scala/actions/workflows/build.yml/badge.svg)](https://github.com/JetBrains/intellij-scala/actions/workflows/build.yml)
[![Discord](https://badgen.net/badge/icon/discord?icon=discord&label)](https://discord.gg/aUKpZzeHCK)

# Scala Plugin for IntelliJ IDEA

The plugin adds support for the Scala language to [IntelliJ IDEA](https://www.jetbrains.com/idea/). \
It enables multiple features such as:
 - Coding assistance (highlighting, completion, formatting, refactorings, code inspection etc.)
 - Navigation, search, information about types and implicits
 - Integration with build tools: SBT, Maven, Gradle, BSP
 - Testing frameworks support (ScalaTest, MUnit, Specs2, uTest)
 - Scala debugger, worksheets and Ammonite scripts 
 - And many more!

(note that HOCON support was moved to a [separate plugin](https://plugins.jetbrains.com/plugin/10481-hocon))

## General information

- To get information about how to install and use this plugin in IDEA, please
  use [IntelliJ IDEA online help](https://www.jetbrains.com/idea/help/scala.html)

- If you have any question about the Scala plugin, we'd be glad to answer it in our 
[discord channel](https://discord.gg/aUKpZzeHCK)

### Reporting issues
If you found a bug, please report it on our [issue tracker](https://youtrack.jetbrains.com/issues/SCL#newissue)

### Contributing
Please see [CONTRIBUTING.md](CONTRIBUTING.md)

## Setting up the project

### Prerequisites
In order to take part in Scala plugin development, you need:

1. IntelliJ IDEA 2022.3 or higher with a compatible version of Scala plugin
2. JDK 17 (you can [download it via IntelliJ IDEA](https://www.jetbrains.com/help/idea/sdk.html#define-sdk))
3. (optional but **recommended**) \
   Enable [internal mode](https://plugins.jetbrains.com/docs/intellij/enabling-internal.html) in IDEA to get access to helpful internal actions and debug information

### Setup

1. Clone this repository to your computer

  ```
  $ git clone https://github.com/JetBrains/intellij-scala.git
  ```

2. Open IntelliJ IDEA. From the Welcome screen or `File` menu, click `Open`, and point to
the directory where you cloned the Scala plugin sources. It will be automatically imported as a sbt project.

3. In the next step, select JDK 17 as project JDK (create it from an installed JDK if necessary).

4. Select the `scalaCommunity` run configuration and select the `Run` or `Debug` button to build and start a
development version of IDEA with the Scala plugin.

## Running the Plugin

### Debugging mode

The easiest way to try your changes is typically to launch the `scalaCommunity` run configuration which is created
when you set up the project as described above.
Under the hood it will launch IntelliJ IDEA with pre-installed Scala Plugin, built from sources.

### As a standalone plugin

To run and distribute a modified version of the plugin in a regular IntelliJ instance, you need to package it.

1. on the sbt shell, run `packageArtifactZip`. This will output the generated plugin zip location
   (typically into `<project directory>/target/scala-plugin.zip`).
2. In IntelliJ, open Preferences, section Plugins, choose "Install plugin from disk..." and navigate to the scala-plugin.zip
3. Restart IntelliJ

## Running the Tests

To run tests properly, the plugin needs to be packaged.
On the sbt shell:

1. `packageArtifact`
2. `runFastTests`

The "fast tests" can take over an hour. To get a quick feedback on project health, run only the type inference tests:

    > runTypeInferenceTests

### GitHub Actions build

The project is configured to build and run the typeInference tests and fast tests with Github Actions. \
The full test suite isn't run to avoid really long build times.

## Common problems
1. **Error `object BuildInfo is already defined ...` during compilation of the project** \
   BuildInfo is an sbt plugin that exposes some of the sbt build metadata to the main project. We use it to forward some dependencies versions from the build sources to main project sources. Sometimes during import this generated source root is added to scala-impl module multiple times. Make sure it's only included once by removing duplicates.

2. **Can't browse IntelliJ Platform sources** \
   When loading Scala Plugin project in sbt, the IntelliJ platform is downloaded to `<home>/.ScalaPluginIC/sdk/<sdk version>/`.
   IntelliJ platform sources should be automatically attached after project has been imported and indices have been built. \
   However, sometimes this doesn't happen and the sources are not attached. As a result you see decompiled code when opening a Platform API class. \
   **Solution:** \
   Invoke "Attach Intellij Sources" action (you need to enable [internal mode](https://plugins.jetbrains.com/docs/intellij/enabling-internal.html) to access this action
3. After building the project you see git local changes in `ImportsPanel.java` (or similar files). All `IdeBorderFactory.PlainSmallWithIndent` are replaced with `BorderFactory` \
**Solution**: enable [internal mode](https://plugins.jetbrains.com/docs/intellij/enabling-internal.html). \
UI Designer uses different border class in internal mode, see `com.intellij.uiDesigner.make.FormSourceCodeGenerator#borderFactoryClassName`
4. **Unexpected local git changes in `uiDesigner.xml` or other files in .idea directory** \
      It may happen due to disable [internal mode](https://plugins.jetbrains.com/docs/intellij/enabling-internal.html) or by enabling it after/during setup. \
      The solution to this problem might be to revert these changes, enable [internal mode](https://plugins.jetbrains.com/docs/intellij/enabling-internal.html) (if it has not already been done) and restart IntelliJ.

## Other
### Investigation performance issues
- YourKit
- There is a "Scala plugin profiler" tool window to track invocations of methods with `@Cached*` or `@Measure` annotations (from `org.jetbrains.plugins.scala.macroAnnotations` package) in real time. The tool window is available in [internal mode](https://plugins.jetbrains.com/docs/intellij/enabling-internal.html) or if `-Dinternal.profiler.tracing=true` is passed to IDEA using [custom VM options](https://www.jetbrains.com/help/idea/tuning-the-ide.html#procedure-jvm-options)


### Generating test coverage reports

You might want to generate a test coverage report for a given package. It can be done by running for example the following:
```
sbt "project scala-impl;set coverageEnabled := true;project scalaCommunity;testOnly org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.*;scala-impl/coverageReport"
```
Close to the very tail of the output of this command you will find a line that gives you the location of the generated report, for example:
```
[info] Written HTML coverage report [/Users/alice/intellij-scala/scala/scala-impl/target/scala-2.13/scoverage-report/index.html]
```
Note that in order to continue working from IntelliJ IDEA again you need to perform Build > Rebuild Project.