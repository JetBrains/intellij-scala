package org.jetbrains.plugins.scala.base

import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.annotation.nowarn

@nowarn("msg=ScalaLightPlatformCodeInsightTestCaseAdapter")
trait TestScalaProjectSettings {

  self: ScalaLightPlatformCodeInsightTestCaseAdapter =>

  def scalaProjectSettings: ScalaProjectSettings =
    ScalaProjectSettings.getInstance(getProject)

}
