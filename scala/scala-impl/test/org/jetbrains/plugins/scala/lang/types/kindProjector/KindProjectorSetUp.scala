package org.jetbrains.plugins.scala.lang.types.kindProjector

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration

trait KindProjectorSetUp extends ScalaLightCodeInsightFixtureTestCase {
  override protected def setUp(): Unit = {
    super.setUp()

    val defaultProfile = ScalaCompilerConfiguration.instanceIn(getProject).defaultProfile
    val newSettings = defaultProfile.getSettings.copy(
      plugins = defaultProfile.getSettings.plugins :+ "kind-projector"
    )
    defaultProfile.setSettings(newSettings)
  }
}
