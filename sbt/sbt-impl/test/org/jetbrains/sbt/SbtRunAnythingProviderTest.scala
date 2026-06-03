package org.jetbrains.sbt

import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.sbt.SbtRunAnythingProvider.SbtShellCommandString
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.settings.SbtSettings
import org.junit.Assert.assertEquals

class SbtRunAnythingProviderTest extends ScalaLightCodeInsightFixtureTestCase with MockSbt_1_0 {

  private def assertMatchingValue(sbtLine: String, expected: SbtShellCommandString): Unit = {
    val dataContext = SimpleDataContext.getProjectContext(getProject)

    // Pretend that the sbt project is a real imported sbt project.
    // We do not do more than SbtRunAnythingProvider expects.
    val sbtSettings = SbtSettings.getInstance(getProject)
    // The project is reused between the tests, so don't link more than once
    if (sbtSettings.getLinkedProjectsSettings.isEmpty) {
      val settings = new SbtProjectSettings()
      settings.setExternalProjectPath("dummyUnusedExternalProjectPath") //required linkProject
      sbtSettings.linkProject(settings)
    }

    val command = new SbtRunAnythingProvider().findMatchingValue(dataContext, sbtLine)
    assertEquals(expected, command)
  }

  def testFindMatchingValue(): Unit = {
    assertMatchingValue(
      """sbt compile""",
      SbtShellCommandString("compile")
    )
  }

  def testFindMatchingValue_UnquoteSbtCommand(): Unit = {
    assertMatchingValue(
      """sbt "compile ; test"""",
      SbtShellCommandString("compile ; test")
    )
  }
}