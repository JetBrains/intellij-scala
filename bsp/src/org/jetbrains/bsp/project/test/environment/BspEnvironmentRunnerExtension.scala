package org.jetbrains.bsp.project.test.environment

import com.intellij.execution.configurations.RunConfiguration
import org.jetbrains.plugins.scala.ExtensionPointDeclaration

sealed trait ExecutionEnvironmentType

object ExecutionEnvironmentType {
  case object TEST extends ExecutionEnvironmentType
  case object RUN extends ExecutionEnvironmentType
}


object BspEnvironmentRunnerExtension
  extends ExtensionPointDeclaration[BspEnvironmentRunnerExtension]("com.intellij.bspEnvironmentRunnerExtension") {

  def getClassExtractor(runConfiguration: RunConfiguration): Option[BspEnvironmentRunnerExtension] =
    implementations.find(_.runConfigurationSupported(runConfiguration))
}

trait BspEnvironmentRunnerExtension {
  /**
   * BSP run/test environment may differ, depending on the BSP target that is chosen.
   * Typically, IntelliJ prompts the user for selection of the BSP target, but in some cases,
   * where the runner is running a certain set of test classes/main classes it may be possible to
   * infer BSP target. This endpoint should return a list of these classes.
   */
  def classes(config: RunConfiguration) : Option[Seq[String]]

  /**
   * Returns true if this extension supports the config
   */
  def runConfigurationSupported(config: RunConfiguration): Boolean

  /**
   * BSP protocol supports two kinds of execution environments: Test Environment and Run Environment.
   * This endpoint is to choose which kind should be taken for the supported run configuration.
   */
  def environmentType: ExecutionEnvironmentType
}
