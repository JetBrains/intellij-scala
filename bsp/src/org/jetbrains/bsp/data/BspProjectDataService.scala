package org.jetbrains.bsp.data

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.util.{DisposeAwareProjectChange, ExternalSystemApiUtil}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.project.external.{AbstractDataService, AbstractImporter, Importer, SdkUtils}
import BspProjectDataService._

class BspProjectDataService extends AbstractDataService[BspProjectData, Project](BspProjectData.Key) {

  override def createImporter(toImport: Seq[DataNode[BspProjectData]], projectData: ProjectData, project: Project, modelsProvider: IdeModifiableModelsProvider): Importer[BspProjectData] = {
    new AbstractImporter[BspProjectData](toImport, projectData, project, modelsProvider) {
      override def importData(): Unit = {
        dataToImport.foreach(node => configureJdk(node.getData)(project))
      }
    }
  }
}

object BspProjectDataService {
  private def configureJdk(data: BspProjectData)(implicit project: ProjectContext): Unit = executeProjectChangeAction {
    val existingJdk = Option(ProjectRootManager.getInstance(project).getProjectSdk)
    val projectJdk =
      Option(data.jdk)
        .flatMap(SdkUtils.findProjectSdk)
        .orElse(existingJdk)
        .orElse(SdkUtils.mostRecentJdk)
    projectJdk.foreach(ProjectRootManager.getInstance(project).setProjectSdk)
  }

  private def executeProjectChangeAction(action: => Unit)(implicit project: ProjectContext): Unit =
    ExternalSystemApiUtil.executeProjectChangeAction(new DisposeAwareProjectChange(project) {
      override def execute(): Unit = action
    })
}