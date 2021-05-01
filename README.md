[![official JetBrains project](https://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![Scala Plugin Build & Test](https://github.com/JetBrains/intellij-scala/actions/workflows/build.yml/badge.svg)](https://github.com/JetBrains/intellij-scala/actions/workflows/build.yml)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/JetBrains/intellij-scala)


# Scala Plugin for IntelliJ IDEA

Plugin that implements Scala, sbt, Play 2, SSP and Hocon support in IntelliJ IDEA.

## General information

- To get information about how to install and use this plugin in IDEA, please
  use [IntelliJ IDEA online help](https://www.jetbrains.com/idea/help/scala.html).

- If you have any question about the Scala plugin, we'd be glad to answer it in [our
  developer community](https://devnet.jetbrains.com/community/idea/scala).

- If you found a bug, please report it on [our issue
  tracker](https://youtrack.jetbrains.com/issues/SCL#newissue).

- If you want to contribute, please see our [intro to the Scala plugin
  internals](https://blog.jetbrains.com/scala/2016/04/21/how-to-contribute-to-intellij-scala-plugin/).

## Setting up the project

### Prerequisites
In order to take part in Scala plugin development, you need:

1. IntelliJ IDEA 2020.1 or higher with a compatible version of Scala plugin
2. JDK 11
3. (optional but recommended) Enable "[internal mode](https://www.jetbrains.org/intellij/sdk/docs/reference_guide/internal_actions/enabling_internal.html)" in IDEA

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

## Browsing IntelliJ platform sources

When loading the plugin in sbt, the IntelliJ platform is downloaded to `<home>/.ScalaPluginIC/sdk/<sdk version>/`. 
IntelliJ platform sources should automatically attach after project has been imported and indices have been built.

However, if this didn't happen, and if you're seeing decompiled code when opening a platform API class you can click
the option "attach sources" at the top of the editor, navigate to the sdk directory and select `sources.zip`,
then choose "All".

## Tests

To run tests properly, the plugin needs to be packaged.
On the sbt shell:

1. `packageArtifact`
2. `runFastTests`

The "fast tests" can take over an hour. To get a quick feedback on project health, run only the type inference tests:

    > runTypeInferenceTests
    
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
