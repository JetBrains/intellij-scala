package org.jetbrains.bsp

import java.net.URL

import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.openapi
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.{ExternalSystemConfigurableAware, ExternalSystemManager}
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.util.Function

class BspExternalSystemManager extends ExternalSystemManager[BspProjectSettings, BspProjectSettingsListener, BspSystemSettings, BspLocalSettings, BspExecutionSettings]
  with ExternalSystemConfigurableAware {
  override def getSystemId: ProjectSystemId = bsp.ProjectSystemId

  override def getSettingsProvider: Function[Project, BspSystemSettings] = BspSystemSettings.getInstance(_)

  override def getLocalSettingsProvider: Function[Project, BspLocalSettings] = BspLocalSettings.getInstance(_)

  override def getExecutionSettingsProvider: Function[openapi.util.Pair[Project, String], BspExecutionSettings] =
    pair => BspExecutionSettings.executionSettingsFor(pair.first, pair.second)

  override def getProjectResolverClass: Class[BspProjectResolver] = classOf[BspProjectResolver]

  override def getTaskManagerClass: Class[BspTaskManager] = classOf[BspTaskManager]

  override def getExternalProjectDescriptor: FileChooserDescriptor = new BspOpenProjectDescriptor

  override def getConfigurable(project: Project): Configurable = new BspExternalSystemConfigurable(project)

  override def enhanceRemoteProcessing(parameters: SimpleJavaParameters): Unit = ()

  override def enhanceLocalProcessing(urls: java.util.List[URL]): Unit = ()
}
