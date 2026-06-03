package org.jetbrains.plugins.scala.internal

import junit.framework.TestCase.assertEquals
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.CompilerTestUtil

class ScalaPluginAboutPopupDescriptionProviderTest extends ScalaLightCodeInsightFixtureTestCase {


  // NOTE: the test primarily checks how the settings are being displayed,
  // it doesn't try to test all the settings exhaustively
  def testGetExtendedDescriptionFormat(): Unit = {
    // Setup: Change settings from default values

    // 1. Application-level settings
    // Use CompilerTestUtil.withModifiedCompileServerSettings to modify and automatically revert settings
    val revertApplicationSettings = CompilerTestUtil.withModifiedCompileServerSettings { settings =>
      settings.COMPILE_SERVER_ENABLED = false
      settings.USE_PROJECT_HOME_AS_WORKING_DIR = true
    }
    revertApplicationSettings.applyChange(this)

    // 2. Project-level settings (no need to revert them as the project won't be reused in other test classes)
    val project = getProject
    println(getProject)

    // SBT settings (skipped as it requires SBT project setup, and I want to make the test fast)
    //val sbtSettings = SbtProjectSettings.forProject(project).get
    //sbtSettings.resolveSbtClassifiers = true
    //sbtSettings.useSeparateCompilerOutputPaths = true
    //sbtSettings.useSbtShellForBuild = true

    // Scala project settings
    val scalaProjectSettings = ScalaProjectSettings.getInstance(project)
    scalaProjectSettings.setCompilerHighlightingScala2(true)
    scalaProjectSettings.setCompilerHighlightingScala3(false)

    // Scala compiler settings
    val compilerConfiguration = ScalaCompilerConfiguration.instanceIn(project)
    compilerConfiguration.incrementalityType = IncrementalityType.IDEA

    //noinspection ApiStatus
    // Get extended description
    val provider = new ScalaPluginAboutPopupDescriptionProvider()
    val extendedDescription = provider.getExtendedDescription

    // Verify the result with a single assertion
    val expectedDescription =
      """Scala plugin:
        |  === compile server settings ===
        |    compile.server.enabled=false
        |    use.project.home.as.working.dir=true
        |  === scala settings for active project ===
        |    compiler.highlighting.scala2.enabled=true
        |    compiler.highlighting.scala3.enabled=false
        |  === compiler settings for active project ===
        |    incrementality.type=IDEA""".stripMargin

    assertEquals(expectedDescription, extendedDescription)
  }
}