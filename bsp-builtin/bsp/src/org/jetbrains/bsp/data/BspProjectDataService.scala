package org.jetbrains.bsp.data

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.impl.LanguageLevelProjectExtensionImpl
import com.intellij.openapi.vcs.roots.VcsRootDetector
import com.intellij.openapi.vcs.{ProjectLevelVcsManager, VcsDirectoryMapping, VcsRoot}
import com.intellij.openapi.vfs.{LocalFileSystem, VfsUtilCore}
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.project.external.{ScalaAbstractProjectDataService, SdkReference, SdkUtils}

import java.nio.file.Path
import java.util
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

class BspProjectDataService extends ScalaAbstractProjectDataService[BspProjectData, Project](BspProjectData.Key) {

  override def importData(
    toImport: util.Collection[? <: DataNode[BspProjectData]],
    projectData: ProjectData,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ): Unit = {
    toImport.forEach { node =>
      val BspProjectData(jdkOpt, vcsRootsCandidates, _) = node.getData
      configureJdk(jdkOpt)(using project)
      configureVcs(vcsRootsCandidates.map(_.toPath), project)
    }
  }

  private def configureVcs(vcsRootsCandidates: collection.Seq[Path], project: Project): Unit = {
    val vcsManager = ProjectLevelVcsManager.getInstance(project)
    val currentVcsRoots = vcsManager.getAllVcsRoots
    val currentMappings = vcsManager.getDirectoryMappings

    val detectedRoots = {
      val detector = project.getService(classOf[VcsRootDetector])
      val detected = mutable.Set[VcsRoot]()
      vcsRootsCandidates
        .iterator
        .map(LocalFileSystem.getInstance.findFileByNioFile)
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
    if (newMappings.nonEmpty) {
      BspVcsRootExtension.onVcsRootAdded(project)
    }
  }

  private def configureJdk(jdk: Option[SdkReference])(implicit project: ProjectContext): Unit = executeProjectChangeAction {
    val existingJdk = Option(ProjectRootManager.getInstance(project).getProjectSdk)
    val projectJdk =
      jdk
        .flatMap(SdkUtils.findOrCreateSdk(_, project))
        .orElse(existingJdk)
        .orElse(SdkUtils.mostRecentRegisteredJdk(project))
    projectJdk.foreach(ProjectRootManager.getInstance(project).setProjectSdk)

    setLanguageLevel(projectJdk, project)
  }

  private def setLanguageLevel(projectJdk: Option[Sdk], project: ProjectContext): Unit = {
    projectJdk.foreach { jdk =>
      Option(LanguageLevel.parse(jdk.getVersionString)).foreach {
        languageLevel =>
          LanguageLevelProjectExtensionImpl.getInstanceImpl(project).setLanguageLevel(languageLevel)
      }
    }
  }
}
