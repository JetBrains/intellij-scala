package org.jetbrains.plugins.scala.lang.types.kindProjector

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration

trait KindProjectorSetUp extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  override def getProjectAdapter: Project = super.getProjectAdapter

  override protected def setUp(): Unit = {
    super.setUp()

    val defaultProfile = ScalaCompilerConfiguration.instanceIn(getProjectAdapter).defaultProfile
    val newSettings = defaultProfile.getSettings.copy(
      plugins = defaultProfile.getSettings.plugins :+ "kind-projector"
    )
    defaultProfile.setSettings(newSettings)
  }
}
