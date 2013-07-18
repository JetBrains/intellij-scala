package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.{ExternalSystemAutoImportAware, ExternalSystemManager}
import com.intellij.openapi.project.Project
import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.externalSystem.service.project.autoimport.CachingExternalSystemAutoImportAware
import com.intellij.util.net.HttpConfigurable
import settings._

/**
 * @author Pavel Fatin
 */
class SbtExternalSystemManager
  extends ExternalSystemManager[SbtProjectSettings, SbtSettingsListener, SbtSettings, SbtLocalSettings, SbtExecutionSettings]
  with ExternalSystemAutoImportAware{

  private val delegate = new CachingExternalSystemAutoImportAware(new SbtAutoImport())

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

  def getSettingsProvider = SbtSettings.getInstance(_: Project)

  def getLocalSettingsProvider = SbtLocalSettings.getInstance(_: Project)

  def getExecutionSettingsProvider = (_: Project, _: String) =>
    new SbtExecutionSettings(SbtExternalSystemManager.proxySettings)

  def getProjectResolverClass = classOf[SbtProjectResolver]

  def getTaskManagerClass = classOf[SbtTaskManager]

  def getAffectedExternalProjectPath(changedFileOrDirPath: String, project: Project) =
    delegate.getAffectedExternalProjectPath(changedFileOrDirPath, project)

  def getExternalProjectDescriptor = new SbtOpenProjectDescriptor()
}

object SbtExternalSystemManager {
  def proxySettings = {
    val http = HttpConfigurable.getInstance

    new ProxySettings(http.USE_HTTP_PROXY && !http.PROXY_TYPE_IS_SOCKS, http.PROXY_HOST, http.PROXY_PORT,
      http.PROXY_AUTHENTICATION, http.PROXY_LOGIN, http.getPlainProxyPassword)
  }
}
