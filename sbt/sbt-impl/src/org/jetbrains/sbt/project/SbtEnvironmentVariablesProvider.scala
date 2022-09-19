package org.jetbrains.sbt.project

import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.plugins.scala.ExtensionPointDeclaration

trait SbtEnvironmentVariablesProvider {
  def getAdditionalVariables(sdk: Sdk): Map[String, String]
}

object SbtEnvironmentVariablesProvider
  extends ExtensionPointDeclaration[SbtEnvironmentVariablesProvider]("com.intellij.sbt.environmentVariableProvider") {

  def computeAdditionalVariables(sdk: Sdk): Map[String, String] =
    implementations
      .map(_.getAdditionalVariables(sdk))
      .fold(Map.empty)(_ ++ _)
}
