package org.jetbrains.plugins.scala.util

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration

trait Source3TestCase extends ScalaLightCodeInsightFixtureTestAdapter {
  abstract override def setUp(): Unit = {
    super.setUp()
    val defaultProfile = ScalaCompilerConfiguration.instanceIn(getFixture.getProject).defaultProfile
    val newSettings    = defaultProfile.getSettings.copy(
      additionalCompilerOptions = Seq("-Xsource:3")
    )
    defaultProfile.setSettings(newSettings)
  }
}
