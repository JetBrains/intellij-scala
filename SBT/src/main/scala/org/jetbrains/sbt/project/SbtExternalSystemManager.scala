package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.{ExternalSystemConfigurableAware, ExternalSystemAutoImportAware, ExternalSystemManager}
import com.intellij.openapi.project.Project
import com.intellij.execution.configurations.SimpleJavaParameters
import settings._
import com.intellij.openapi.externalSystem.util._
import com.intellij.openapi.externalSystem.service.project.autoimport.CachingExternalSystemAutoImportAware
import com.intellij.util.net.HttpConfigurable
import org.jetbrains.sbt.settings.SbtApplicationSettings
import java.util
import java.net.URL
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.projectRoots.{JavaSdkType, Sdk, ProjectJdkTable}
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import java.io.File
import com.intellij.openapi.roots.ProjectRootManager

/**
 * @author Pavel Fatin
 */
class SbtExternalSystemManager
  extends ExternalSystemManager[SbtProjectSettings, SbtSettingsListener, SbtSettings, SbtLocalSettings, SbtExecutionSettings]
  with ExternalSystemAutoImportAware with ExternalSystemConfigurableAware {

  def enhanceLocalProcessing(urls: util.List[URL]) {
    urls.add(jarWith[scala.App].toURI.toURL)
  }

  private val delegate = new CachingExternalSystemAutoImportAware(new SbtAutoImport())

  def enhanceRemoteProcessing(parameters: SimpleJavaParameters) {
    val classpath = parameters.getClassPath

    classpath.add(jarWith[this.type])
    classpath.add(jarWith[scala.App])
    classpath.add(jarWith[scala.xml.Node])

//    val vmParameters = parameters.getVMParametersList
//    vmParameters.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")

    parameters.getVMParametersList.addProperty(
      ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY, SbtProjectSystem.Id.getId)
  }

  def getSystemId = SbtProjectSystem.Id

  def getSettingsProvider = SbtSettings.getInstance _

  def getLocalSettingsProvider = SbtLocalSettings.getInstance _

  def getExecutionSettingsProvider = SbtExternalSystemManager.executionSettingsFor _

  def getProjectResolverClass = classOf[SbtProjectResolver]

  def getTaskManagerClass = classOf[SbtTaskManager]

  def getAffectedExternalProjectPath(changedFileOrDirPath: String, project: Project) =
    delegate.getAffectedExternalProjectPath(changedFileOrDirPath, project)

  def getExternalProjectDescriptor = new SbtOpenProjectDescriptor()

  def getConfigurable(project: Project): Configurable = new SbtExternalSystemConfigurable(project)
}

object SbtExternalSystemManager {
  def executionSettingsFor(project: Project, path: String) = {
    val appSettings = SbtApplicationSettings.instance

    val customLauncher = appSettings.customLauncherEnabled
      .option(appSettings.getCustomLauncherPath).map(_.toFile)

    val vmOptions = Seq(s"-Xmx${appSettings.getMaximumHeapSize}M") ++
      appSettings.getVmParameters.split("\\s+").toSeq ++
      proxyOptionsFor(HttpConfigurable.getInstance)

    val customVmExecutable = appSettings.customVMEnabled.option(appSettings.getCustomVMPath).map(_.toFile)

    val settings = SbtSettings.getInstance(project)

    val projectSettings = settings.getLinkedProjectSettings(path)

    val vmExecutable = customVmExecutable.orElse {
      val projectSdk = projectSettings.jdkName.flatMap(name => Option(ProjectJdkTable.getInstance().findJdk(name)))
              .orElse(Option(ProjectRootManager.getInstance(project).getProjectSdk))

      projectSdk.map { sdk =>
        val sdkType = sdk.getSdkType.asInstanceOf[JavaSdkType]
        new File(sdkType.getVMExecutablePath(sdk))
      }
    } getOrElse {
      throw new ExternalSystemException("Cannot determine Java VM executable in selected JDK")
    }

    new SbtExecutionSettings(vmExecutable, vmOptions, customLauncher, projectSettings.jdkName,
      projectSettings.resolveClassifiers, projectSettings.resolveSbtClassifiers)
  }

  private def proxyOptionsFor(http: HttpConfigurable): Seq[String] = {
    val useProxy = http.USE_HTTP_PROXY && !http.PROXY_TYPE_IS_SOCKS
    val useCredentials = useProxy && http.PROXY_AUTHENTICATION

    useProxy.seq(s"-Dhttp.proxyHost=${http.PROXY_HOST}", s"-Dhttp.proxyPort=${http.PROXY_PORT}") ++
      useCredentials.seq(s"-Dhttp.proxyUser=${http.PROXY_LOGIN}", s"-Dhttp.proxyPassword=${http.getPlainProxyPassword}")
  }
}
