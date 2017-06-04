package org.jetbrains.plugins.cbt.project

import java.net.URL
import java.util

import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.util.Function
import org.jetbrains.plugins.cbt.CbtProjectResolver
import org.jetbrains.plugins.cbt.project.settings._

class CbtExternalSystemManager
  extends ExternalSystemManager[CbtProjectSettings, CbtProjectSettingsListener,
    CbtSystemSettings, CbtLocalSettings, CbtExecutionSettings] {

  override def getTaskManagerClass: Class[CbtTaskManager] = classOf[CbtTaskManager]

  override def getSystemId: ProjectSystemId = CbtProjectSystem.Id

  override def getExternalProjectDescriptor: FileChooserDescriptor = new CbtOpenProjectDescriptor

  override def getExecutionSettingsProvider: Function[Pair[Project, String], CbtExecutionSettings] =
    new Function[Pair[Project, String], CbtExecutionSettings]() {
      override def fun(pair: Pair[Project, String]): CbtExecutionSettings =
        new CbtExecutionSettings(pair.second)
    }

  override def getProjectResolverClass: Class[CbtProjectResolver] = classOf[CbtProjectResolver]

  override def getLocalSettingsProvider: Function[Project, CbtLocalSettings] =
    new Function[Project, CbtLocalSettings]() {
      override def fun(project: Project): CbtLocalSettings = CbtLocalSettings.getInstance(project)
    }

  override def getSettingsProvider: Function[Project, CbtSystemSettings] =
    new Function[Project, CbtSystemSettings]() {
      override def fun(project: Project): CbtSystemSettings = CbtSystemSettings.getInstance(project)
    }

  override def enhanceLocalProcessing(urls: util.List[URL]): Unit = {}

  override def enhanceRemoteProcessing(parameters: SimpleJavaParameters): Unit = {}
}
