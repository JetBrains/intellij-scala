package org.jetbrains.plugins.cbt.project

import java.net.URL
import java.util

import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.util.Function
import org.jetbrains.plugins.cbt.project.settings._
import org.jetbrains.sbt.jarWith

import scala.collection.JavaConverters._
import org.jetbrains.plugins.cbt._

class CbtExternalSystemManager
  extends ExternalSystemManager[CbtProjectSettings, CbtProjectSettingsListener,
    CbtSystemSettings, CbtLocalSettings, CbtExecutionSettings] {

  override def getTaskManagerClass: Class[CbtTaskManager] = classOf[CbtTaskManager]

  override def getSystemId: ProjectSystemId = CbtProjectSystem.Id

  override def getExternalProjectDescriptor: FileChooserDescriptor = new CbtOpenProjectDescriptor

  override def getExecutionSettingsProvider: Function[Pair[Project, String], CbtExecutionSettings] =
    (project: Project, path: String) => {
        val projectSettings = CbtProjectSettings.getInstance(project, path)
        new CbtExecutionSettings(path,
          projectSettings.isCbt,
          projectSettings.useCbtForInternalTasks,
          projectSettings.useDirect,
          projectSettings.extraModules.asScala)
      }

  override def getProjectResolverClass: Class[CbtProjectResolver] = classOf[CbtProjectResolver]

  override val getLocalSettingsProvider: Function[Project, CbtLocalSettings] =
    (project: Project) => CbtLocalSettings.getInstance(project)

  override def getSettingsProvider: Function[Project, CbtSystemSettings] =
    (project: Project) =>CbtSystemSettings.instance(project)

  override def enhanceLocalProcessing(urls: util.List[URL]): Unit = {
    urls.add(jarWith[scala.App].toURI.toURL)
  }

  override def enhanceRemoteProcessing(parameters: SimpleJavaParameters): Unit = {
    val classpath = parameters.getClassPath

    classpath.add(jarWith[this.type])
    classpath.add(jarWith[org.jetbrains.sbt.structure.XmlSerializer[_]])
    classpath.add(jarWith[scala.App])
    classpath.add(jarWith[scala.xml.Node])

    parameters.getVMParametersList.addProperty(
      ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY, CbtProjectSystem.Id.getId)

    parameters.getVMParametersList.addProperty(
      PathManager.PROPERTY_LOG_PATH, PathManager.getLogPath)
  }
}
