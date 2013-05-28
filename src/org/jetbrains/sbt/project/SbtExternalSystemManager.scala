package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.project.Project
import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import settings._

/**
 * @author Pavel Fatin
 */
class SbtExternalSystemManager extends ExternalSystemManager[SbtProjectSettings, SbtSettingsListener, SbtSettings, SbtLocalSettings, SbtExecutionSettings] {
  def enhanceParameters(parameters: SimpleJavaParameters) {
    val classpath = parameters.getClassPath

    classpath.add(jarWith[this.type])
    classpath.add(jarWith[scala.App])
    classpath.add(jarWith[ExternalSystemBundle])

    val vmParameters = parameters.getVMParametersList
    vmParameters.addParametersString(System.getenv("JAVA_OPTS"))
//    vmParameters.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")
  }

  def getSystemId = SbtProjectSystem.Id

  def isReady(project: Project) = true

  def getSettingsProvider = SbtSettings.getInstance(_: Project)

  def getLocalSettingsProvider = SbtLocalSettings.getInstance(_: Project)

  def getExecutionSettingsProvider = new SbtExecutionSettings(_: Project, _: String)

  def getProjectResolverClass = classOf[SbtProjectResolver]

  def getBuildManagerClass = classOf[SbtBuildManager]
}
