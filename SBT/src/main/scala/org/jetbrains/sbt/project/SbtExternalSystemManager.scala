package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.{ExternalSystemAutoImportAware, ExternalSystemManager}
import com.intellij.openapi.project.Project
import com.intellij.execution.configurations.SimpleJavaParameters
import settings._
import com.intellij.openapi.externalSystem.util._
import com.intellij.openapi.externalSystem.service.project.autoimport.CachingExternalSystemAutoImportAware
import com.intellij.util.net.HttpConfigurable
import org.jetbrains.sbt.settings.SbtApplicationSettings
import java.util
import java.net.URL
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import java.util.Collections

/**
 * @author Pavel Fatin
 */
class SbtExternalSystemManager
  extends ExternalSystemManager[SbtProjectSettings, SbtSettingsListener, ScalaSbtSettings, SbtLocalSettings, SbtExecutionSettings]
  with ExternalSystemAutoImportAware /*with ExternalSystemConfigurableAware*/ with StartupActivity {
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
//    vmParameters.addParametersString("-Xmx256M")

    parameters.getVMParametersList.addProperty(ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY,
      SbtProjectSystem.Id.getId)
//    vmParameters.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")
  }

  def getSystemId = SbtProjectSystem.Id

  def getSettingsProvider = ScalaSbtSettings.getInstance _

  def getLocalSettingsProvider = SbtLocalSettings.getInstance _

  def getExecutionSettingsProvider = SbtExternalSystemManager.executionSettingsFor _

  def getProjectResolverClass = classOf[SbtProjectResolver]

  def getTaskManagerClass = classOf[SbtTaskManager]

  def getAffectedExternalProjectPath(changedFileOrDirPath: String, project: Project) =
    delegate.getAffectedExternalProjectPath(changedFileOrDirPath, project)

  def getExternalProjectDescriptor = new SbtOpenProjectDescriptor()

  /**
   * I have no idea what I am doing 
   * @author Dmitry
   */
  def runActivity(project: Project) {
    val connection = project.getMessageBus connect project
    val sbtSettings = ScalaSbtSettings getInstance project
    
    connection.subscribe(sbtSettings.getChangesTopic, new SbtSettingsListener {
      def onProjectsLinked(settings: util.Collection[SbtProjectSettings]) {
        val projectDataManager = ServiceManager.getService(classOf[ProjectDataManager])
        
        import scala.collection.JavaConverters._
        
        settings.asScala foreach {
          case setting => 
//            ExternalSystemUtil.refreshProjects(project, SbtProjectSystem.Id, false, ProgressExecutionMode.MODAL_SYNC)
//            
            ExternalSystemUtil.refreshProject(project, SbtProjectSystem.Id, setting.getExternalProjectPath, new ExternalProjectRefreshCallback {
              def onFailure(errorMessage: String, errorDetails: String) {}

              def onSuccess(externalProject: DataNode[ProjectData]) {
                if (externalProject == null) return

                ExternalSystemApiUtil.executeProjectChangeAction(true, new DisposeAwareProjectChange(project) {
                  def execute() {
                    ProjectRootManagerEx getInstanceEx project mergeRootsChangesDuring new Runnable {
                      def run() {
                        projectDataManager.importData(externalProject.getKey, Collections.singleton[DataNode[ProjectData]](externalProject), project, true)
                      }
                    }
                  }
                })
              }
            }, false, ProgressExecutionMode.MODAL_SYNC)
        }
      }

      def onBulkChangeEnd() {}

      def onBulkChangeStart() {}

      def onProjectRenamed(oldName: String, newName: String) {}

      def onProjectsUnlinked(linkedProjectPaths: util.Set[String]) {}

      def onUseAutoImportChange(currentValue: Boolean, linkedProjectPath: String) {}
    })
  }

//  def getConfigurable(project: Project): Configurable = new SbtExternalSystemConfigurable(project)
}

object SbtExternalSystemManager {
  def executionSettingsFor(project: Project, path: String) = {
    val app = SbtApplicationSettings.instance

    val customLauncher = app.customLauncherEnabled
      .option(app.getCustomLauncherPath).map(_.toFile)

    val vmOptions = Seq(s"-Xmx${app.getMaximumHeapSize}M") ++
      app.getVmParameters.split("\\s+").toSeq ++
      proxyOptionsFor(HttpConfigurable.getInstance)

    new SbtExecutionSettings(vmOptions, customLauncher)
  }

  private def proxyOptionsFor(http: HttpConfigurable): Seq[String] = {
    val useProxy = http.USE_HTTP_PROXY && !http.PROXY_TYPE_IS_SOCKS
    val useCredentials = useProxy && http.PROXY_AUTHENTICATION

    useProxy.seq(s"-Dhttp.proxyHost=${http.PROXY_HOST}", s"-Dhttp.proxyPort=${http.PROXY_PORT}") ++
      useCredentials.seq(s"-Dhttp.proxyUser=${http.PROXY_LOGIN}", s"-Dhttp.proxyPassword=${http.getPlainProxyPassword}")
  }
}
