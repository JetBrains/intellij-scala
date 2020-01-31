package org.jetbrains.bsp.project

import java.io.File
import java.util

import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.openapi
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.{ExternalSystemAutoImportAware, ExternalSystemConfigurableAware, ExternalSystemManager}
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.util.Function
import org.jetbrains.bsp._
import org.jetbrains.bsp.project.resolver.BspProjectResolver
import org.jetbrains.bsp.settings._

import scala.collection.JavaConverters._

class BspExternalSystemManager extends ExternalSystemManager[BspProjectSettings, BspProjectSettingsListener, BspSettings, BspLocalSettings, BspExecutionSettings]
  with ExternalSystemConfigurableAware
  with ExternalSystemAutoImportAware
{

  override def getSystemId: ProjectSystemId = BSP.ProjectSystemId

  override def getSettingsProvider: Function[Project, BspSettings] = BspSettings.getInstance(_)

  override def getLocalSettingsProvider: Function[Project, BspLocalSettings] = BspLocalSettings.getInstance(_)

  override def getExecutionSettingsProvider: Function[openapi.util.Pair[Project, String], BspExecutionSettings] =
    pair => BspExecutionSettings.executionSettingsFor(new File(pair.second))

  override def getProjectResolverClass: Class[BspProjectResolver] = classOf[BspProjectResolver]

  override def getTaskManagerClass: Class[BspTaskManager] = classOf[BspTaskManager]

  override def getExternalProjectDescriptor: FileChooserDescriptor = new BspOpenProjectDescriptor

  override def getConfigurable(project: Project): Configurable = new BspExternalSystemConfigurable(project)

  override def enhanceRemoteProcessing(parameters: SimpleJavaParameters): Unit = ()

  override def getAffectedExternalProjectPath(changedFileOrDirPath: String, project: Project): String = {
    val file = new File(changedFileOrDirPath)
    val isConfigFile = (BspUtil.isBspConfigFile(file) || BspUtil.isBloopConfigFile(file)) &&
      BspUtil.workspaces(project).contains(file.getParentFile.toPath)

    if (isConfigFile) file.getParentFile.getAbsolutePath
    else null
  }

  override def getAffectedExternalProjectFiles(projectPath: String, project: Project): util.List[File] = {
    val workspace = new File(projectPath)
    val bspConfigs = BspUtil.bspConfigFiles(workspace)
    val bloopConfigs = BspUtil.bloopConfigDir(workspace).toList
      .flatMap(_.listFiles(file => file.getName.endsWith(".json")).toList)

    (bspConfigs ++ bloopConfigs).asJava
  }
}
