package org.jetbrains.plugins.cbt.project.data.service

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdk, ProjectJdkTable}
import com.intellij.openapi.roots.ProjectRootManager
import org.jetbrains.plugins.cbt.project.data.service
import org.jetbrains.plugins.cbt.structure.CbtProjectData
import org.jetbrains.sbt.project.data.service.{AbstractDataService, AbstractImporter, Importer}

class CbtProjectDataService extends AbstractDataService[CbtProjectData, Project](CbtProjectData.Key) {
  override def createImporter(toImport: Seq[DataNode[CbtProjectData]],
                              projectData: ProjectData,
                              project: Project,
                              modelsProvider: IdeModifiableModelsProvider): Importer[CbtProjectData] =
    new service.CbtProjectDataService.Importer(toImport, projectData, project, modelsProvider)
}

object CbtProjectDataService {

  private class Importer(toImport: Seq[DataNode[CbtProjectData]],
                         projectData: ProjectData,
                         project: Project,
                         modelsProvider: IdeModifiableModelsProvider)
    extends AbstractImporter[CbtProjectData](toImport, projectData, project, modelsProvider) {

    override def importData(): Unit = {
      dataToImport.foreach(node => doImport(node.getData))
    }

    private def doImport(dataNode: CbtProjectData): Unit = executeProjectChangeAction {
      val javaSdk = ProjectJdkTable.getInstance().findMostRecentSdkOfType(JavaSdk.getInstance)
      ProjectRootManager.getInstance(project).setProjectSdk(javaSdk)
    }
  }

}