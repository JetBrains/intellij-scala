[![official JetBrains project](https://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![TC Build Status](https://teamcity.jetbrains.com/app/rest/builds/buildType:(id:Scala_Tests)/statusIcon.svg)](https://teamcity.jetbrains.com/viewType.html?buildTypeId=Scala_Tests&guest=1)
[![Travis Build Status](https://travis-ci.org/JetBrains/intellij-scala.svg)](https://travis-ci.org/JetBrains/intellij-scala) 
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

1. IntelliJ IDEA 2019.1 or higher with a compatible version of Scala plugin
2. JDK 11 (recommended: [JetBrains JDK](https://bintray.com/jetbrains/intellij-jdk))

### Setup

1. Clone this repository to your computer

  ```
  $ git clone https://github.com/JetBrains/intellij-scala.git
  ```

2. Open IntelliJ IDEA, select `File -> New -> Project from existing sources`, point to
the directory where Scala plugin repository is and then import it as sbt project. On the sbt settings page, select the use sbt shell for build and import option and click OK.

3. In the next step, select JDK 11 as project JDK (create it from an installed JDK if necessary).

4. After importing is completed, to create artifacts and run configurations for IDEA project,
   run these commands in the *sbt shell*:
   
  ```
  > ;createIDEAArtifactXml ;idea-runner/createIDEARunConfiguration
  ```

5. Select the IDEA run configuration and select the `Run` or `Debug` button to build and start a development version
of IDEA with the Scala plugin.

## Browsing IntelliJ platform sources

When loading the plugin in sbt, the IntelliJ platform is downloaded to 
`<home>/.ScalaPluginIC/sdk/<sdk version>/`. 
When opening a platform API class you will see the option to "attach sources". 
Click it, navigate to the sdk directory and select `sources.zip`, then choose "All".

## Tests

To run tests properly, the plugin needs to be packaged.
On the sbt shell:

1. `packagePluginCommunity`
2. `runFastTests`

The "fast tests" can take over an hour. To get a quick feedback on project health, run the "typeInference tests"

    > testOnly org.jetbrains.plugins.scala.lang.typeInference.*
    
## Travis CI

The project is configured to build and run the typeInference tests with Travis CI, which you can enable in your forks.
The full test suite can't currently be run because Travis doesn't allow builds to take that long.

## Running the plugin

### Debugging mode

The easiest way to try your changes is typically to launch the `IDEA` run configuration which is created when you 
set up the project as described above.

### As a standalone plugin

To run and distribute a modified version of the plugin in a regular IntelliJ instance, you need to package it.

1. on the sbt shell, run `packagePluginZip`. This will output the generated plugin zip location
   (typically into `<project directory>/target/scala-plugin.zip`).
2. In IntelliJ, open Preferences, section Plugins, choose "Install plugin from disk..." and navigate to the scala-plugin.zip
3. Restart IntelliJ
