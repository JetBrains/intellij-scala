package org.jetbrains.plugins.scala.base

import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

trait TestScalaProjectSettings {

  self: ScalaLightPlatformCodeInsightTestCaseAdapter =>

  def scalaProjectSettings: ScalaProjectSettings =
    ScalaProjectSettings.getInstance(getProjectAdapter)

}
