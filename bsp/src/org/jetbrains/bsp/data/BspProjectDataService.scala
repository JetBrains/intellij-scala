package org.jetbrains.bsp.data

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import org.jetbrains.bsp.data.BspProjectDataService._
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.project.external.JdkByHome
import org.jetbrains.plugins.scala.project.external.SdkReference
import org.jetbrains.plugins.scala.project.external.AbstractDataService
import org.jetbrains.plugins.scala.project.external.AbstractImporter
import org.jetbrains.plugins.scala.project.external.Importer
import org.jetbrains.plugins.scala.project.external.SdkUtils

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
        .flatMap(findOrCreateSdkFromBsp)
        .orElse(existingJdk)
        .orElse(SdkUtils.mostRecentJdk)
    projectJdk.foreach(ProjectRootManager.getInstance(project).setProjectSdk)
  }

  private def findOrCreateSdkFromBsp(sdkReference: SdkReference): Option[Sdk] = {
    def createFromHome = {
      Option(sdkReference).collect {
        case JdkByHome(home) =>
          val suffix = if (home.getName == "jre") home.getParentFile.getName else home.getName
          val name = s"BSP_$suffix"
          val newJdk = JavaSdk.getInstance.createJdk(name, home.toString)
          ProjectJdkTable.getInstance.addJdk(newJdk)
          newJdk
      }
    }

    SdkUtils.findProjectSdk(sdkReference).orElse(createFromHome)
  }

  private def executeProjectChangeAction(action: => Unit)(implicit project: ProjectContext): Unit =
    ExternalSystemApiUtil.executeProjectChangeAction(new DisposeAwareProjectChange(project) {
      override def execute(): Unit = action
    })
}