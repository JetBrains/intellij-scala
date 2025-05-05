package org.jetbrains.plugins.scala.internal

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.{ScalaCompileServerSettings, ScalaProjectSettings}
import org.junit.jupiter.api.Assertions._

class ScalaPluginAboutPopupDescriptionProviderTest extends ScalaLightCodeInsightFixtureTestCase {

  // NOTE: the test primarily checks how the settings are being displayed,
  // it doesn't try to test all the settings exhaustively
  def testGetExtendedDescriptionFormat(): Unit = {
    // Setup: Change settings from default values

    // 1. Application-level settings
    val compileServerSettings = ScalaCompileServerSettings.getInstance()
    compileServerSettings.COMPILE_SERVER_ENABLED = false
    compileServerSettings.USE_PROJECT_HOME_AS_WORKING_DIR = true

    // 2. Project-level settings
    val project = getProject()

    // SBT settings (skipped as it requires SBT project setup, and I want to make the test fast)
    //val sbtSettings = SbtProjectSettings.forProject(project).get
    //sbtSettings.resolveSbtClassifiers = true
    //sbtSettings.useSeparateCompilerOutputPaths = true
    //sbtSettings.useSbtShellForBuild = true

    // Scala project settings
    val scalaProjectSettings = ScalaProjectSettings.getInstance(project)
    scalaProjectSettings.setCompilerHighlightingScala2(true)
    scalaProjectSettings.setCompilerHighlightingScala3(false)
    scalaProjectSettings.setUseCompilerRanges(false)

    // Scala compiler settings
    val compilerConfiguration = ScalaCompilerConfiguration.instanceIn(project)
    compilerConfiguration.incrementalityType = IncrementalityType.IDEA

    //noinspection ApiStatus
    // Get extended description
    val provider = new ScalaPluginAboutPopupDescriptionProvider()
    val extendedDescription = provider.getExtendedDescription()

    // Verify the result with a single assertion
    val expectedDescription =
      """Scala plugin:
        |  === compile server settings ===
        |    compile.server.enabled=false
        |    use.project.home.as.working.dir=true
        |  === scala setting for active project ===
        |    compiler.highlighting.scala2.enabled=true
        |    compiler.highlighting.scala3.enabled=false
        |    compiler.highlighting.use.compiler.ranges=false
        |  === compiler settings for active project ===
        |    incrementality.type=IDEA""".stripMargin

    assertEquals(expectedDescription, extendedDescription)
  }
}