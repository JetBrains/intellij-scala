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
import com.intellij.openapi.roots.impl.LanguageLevelProjectExtensionImpl
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vcs.roots.VcsRootDetector
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.bsp.data.BspProjectDataService._
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.project.external.JdkByHome
import org.jetbrains.plugins.scala.project.external.SdkReference
import org.jetbrains.plugins.scala.project.external.AbstractDataService
import org.jetbrains.plugins.scala.project.external.AbstractImporter
import org.jetbrains.plugins.scala.project.external.Importer
import org.jetbrains.plugins.scala.project.external.SdkUtils

import scala.jdk.CollectionConverters._
import scala.collection.mutable


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

  private def configureVcs(vcsRootsCandidates: collection.Seq[File], project: Project): Unit = {
    val vcsManager = ProjectLevelVcsManager.getInstance(project)
    val currentVcsRoots = vcsManager.getAllVcsRoots
    val currentMappings = vcsManager.getDirectoryMappings

    val detectedRoots = {
      val detector = ServiceManager.getService(project, classOf[VcsRootDetector])
      val detected = mutable.Set[VcsRoot](currentVcsRoots.toIndexedSeq: _*)
      vcsRootsCandidates
        .iterator
        .map(LocalFileSystem.getInstance.findFileByIoFile)
        .filter(_ != null)
        .foreach { virtualFile =>
          val isUnderDetectedVcsRoot = VfsUtilCore.isUnder(virtualFile, detected.map(_.getPath).asJava)
          if (!isUnderDetectedVcsRoot) {
            val roots = detector.detect(virtualFile).asScala
            detected ++= roots
          }
        }
      detected
    }

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

    setLanguageLevel(projectJdk, project)
  }

  private def setLanguageLevel(projectJdk: Option[Sdk], project: ProjectContext) = {
    projectJdk.foreach { jdk =>
      Option(LanguageLevel.parse(jdk.getVersionString)).foreach {
        languageLevel =>
          LanguageLevelProjectExtensionImpl.getInstanceImpl(project).setLanguageLevel(languageLevel)
      }
    }
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
