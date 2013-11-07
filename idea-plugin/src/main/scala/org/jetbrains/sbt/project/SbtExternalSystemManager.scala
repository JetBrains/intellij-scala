package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.{ExternalSystemAutoImportAware, ExternalSystemManager}
import com.intellij.openapi.project.Project
import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.openapi.externalSystem.util.{ExternalSystemConstants, ExternalSystemBundle}
import com.intellij.openapi.externalSystem.service.project.autoimport.CachingExternalSystemAutoImportAware
import com.intellij.util.net.HttpConfigurable
import org.jetbrains.sbt.settings.SbtApplicationSettings
import settings._
import java.util
import java.net.URL
import java.io.File
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.Processor

/**
 * @author Pavel Fatin
 */
class SbtExternalSystemManager
  extends ExternalSystemManager[SbtProjectSettings, SbtSettingsListener, SbtSettings, SbtLocalSettings, SbtExecutionSettings]
  with ExternalSystemAutoImportAware{
  def enhanceLocalProcessing(urls: util.List[URL]) {
    urls.add(jarWith[scala.App].toURI.toURL)
  }

  private val delegate = new CachingExternalSystemAutoImportAware(new SbtAutoImport())

  def enhanceRemoteProcessing(parameters: SimpleJavaParameters) {
    val classpath = parameters.getClassPath

    classpath.add(jarWith[this.type])
    classpath.add(jarWith[scala.App])
    classpath.add(jarWith[ExternalSystemBundle])

    val vmParameters = parameters.getVMParametersList
    vmParameters.addParametersString(System.getenv("JAVA_OPTS"))
    vmParameters.addParametersString("-Xmx256M")

    parameters.getVMParametersList.addProperty(ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY,
      SbtProjectSystem.Id.getId)
//    vmParameters.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")
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
}

object SbtExternalSystemManager {
  def executionSettingsFor(project: Project, path: String) = {
    val app = SbtApplicationSettings.instance

    val customLauncher = app.customLauncherEnabled
      .option(app.getCustomLauncherPath).map(_.toFile)

    val vmOptions = Seq(s"-Xmx${app.getMaximumHeapSize}M") ++
      app.getVmParameters.split("\\s+").toSeq ++
      proxyOptionsFor(HttpConfigurable.getInstance) ++
      SbtOptionsProvider.vmOptionsFor(project, path)

    var rootPath: File = path.toFile

    var isSbt12 = false
    if (rootPath.exists) {
      if (!rootPath.isDirectory) rootPath = rootPath.getParentFile
      if (rootPath != null) {
        FileUtil.processFilesRecursively(rootPath, new Processor[File] {
          def process(t: File): Boolean = {
            if (t.getName == "build.properties") {
              val SbtVersion = """\s*sbt.version\s*=\s*([0-9]*)\.([0-9]*)(\.[0-9]*)?\s*""".r
              FileUtil.loadFile(t).split("\n").foreach {
                case SbtVersion(_, s,_) =>
                  if (s.toInt < 13) isSbt12 = true
                  return false
                case _ =>
              }
            }
            true
          }
        })
      }
    }

    new SbtExecutionSettings(vmOptions, customLauncher, isSbt12)
  }

  private def proxyOptionsFor(http: HttpConfigurable): Seq[String] = {
    val useProxy = http.USE_HTTP_PROXY && !http.PROXY_TYPE_IS_SOCKS
    val useCredentials = useProxy && http.PROXY_AUTHENTICATION

    useProxy.seq(s"-Dhttp.proxyHost=${http.PROXY_HOST}", s"-Dhttp.proxyPort=${http.PROXY_PORT}") ++
      useCredentials.seq(s"-Dhttp.proxyUser=${http.PROXY_LOGIN}", s"-Dhttp.proxyPassword=${http.getPlainProxyPassword}")
  }
}
