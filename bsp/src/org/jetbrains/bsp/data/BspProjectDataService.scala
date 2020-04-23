package org.jetbrains.bsp.data

import java.io.File

import com.intellij.openapi.components.ServiceManager
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
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vcs.roots.VcsRootDetector
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.bsp.data.BspProjectDataService._
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.project.external.JdkByHome
import org.jetbrains.plugins.scala.project.external.SdkReference
import org.jetbrains.plugins.scala.project.external.AbstractDataService
import org.jetbrains.plugins.scala.project.external.AbstractImporter
import org.jetbrains.plugins.scala.project.external.Importer
import org.jetbrains.plugins.scala.project.external.SdkUtils

import scala.collection.JavaConverters._


class BspProjectDataService extends AbstractDataService[BspProjectData, Project](BspProjectData.Key) {

  override def createImporter(toImport: Seq[DataNode[BspProjectData]], projectData: ProjectData, project: Project, modelsProvider: IdeModifiableModelsProvider): Importer[BspProjectData] = {
    new AbstractImporter[BspProjectData](toImport, projectData, project, modelsProvider) {
      override def importData(): Unit = {
        dataToImport.foreach { node =>
          configureJdk(Option(node.getData.jdk))(project)
          configureVcs(node.getData.vcsRootsCandidates.asScala, project)
        }
      }
    }
  }
}

object BspProjectDataService {

  private def configureVcs(vcsRootsCandidates: Seq[File], project: Project): Unit = {
    val detectedRoots = {
      val detector = ServiceManager.getService(project, classOf[VcsRootDetector])
      vcsRootsCandidates.flatMap { candidate =>
        val virtualFile = LocalFileSystem.getInstance.findFileByIoFile(candidate)
        detector.detect(virtualFile).asScala
      }.distinct
    }

    val vcsManager = ProjectLevelVcsManager.getInstance(project)
    val currentVcsRoots = vcsManager.getAllVcsRoots
    val currentMappings = vcsManager.getDirectoryMappings

    val newMappings = detectedRoots
      .filterNot(currentVcsRoots.contains)
      .map(root => new VcsDirectoryMapping(root.getPath.getPath, root.getVcs.getName))
    val allMappings = (currentMappings.asScala ++ newMappings).asJava

    vcsManager.setDirectoryMappings(allMappings)
  }

  private def configureJdk(jdk: Option[SdkReference])(implicit project: ProjectContext): Unit = executeProjectChangeAction {
    val existingJdk = Option(ProjectRootManager.getInstance(project).getProjectSdk)
    val projectJdk =
      jdk
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