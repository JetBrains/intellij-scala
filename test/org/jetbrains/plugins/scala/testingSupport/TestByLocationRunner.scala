package org.jetbrains.plugins.scala.testingSupport

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.openapi.project.Project

/**
 * @author Roman.Shein
 * @since 20.01.2015.
 */
trait TestByLocationRunner {
  protected def createTestFromLocation(lineNumber: Int, offset: Int, fileName: String): RunnerAndConfigurationSettings

  protected def runTestFromConfig(
                                     configurationCheck: RunnerAndConfigurationSettings => Boolean,
                                     runConfig: RunnerAndConfigurationSettings,
                                     checkOutputs: Boolean = false,
                                     duration: Int = 3000,
                                     debug: Boolean = false
                                     ): (String, Option[AbstractTestProxy])

  def getProject: Project

  protected def addFileToProject(fileName: String, fileText: String)
}
