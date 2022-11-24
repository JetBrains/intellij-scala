package org.jetbrains.sbt

import com.intellij.ide.actions.CreateDirectoryCompletionContributor
import com.intellij.ide.actions.CreateDirectoryCompletionContributor.Variant
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.model.project.{ContentRootData, ExternalSystemSourceType, ModuleData}
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiDirectory
import org.jetbrains.jps.model.java.{JavaResourceRootType, JavaSourceRootType}
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.sbt.SbtDirectoryCompletionContributor.getModuleContentRootsData
import org.jetbrains.sbt.project.SbtProjectSystem

import java.util
import java.util.Collections.emptyList
import scala.collection.mutable
import scala.jdk.CollectionConverters.{BufferHasAsJava, CollectionHasAsScala}

/**
 * Inspired by `org.jetbrains.plugins.gradle.GradleDirectoryCompletionContributor`
 *
 * NOTE: currently there is no way to apply custom sorting to the completion items, see IDEA-306694
 */
class SbtDirectoryCompletionContributor extends CreateDirectoryCompletionContributor {
  override def getDescription: String = SbtBundle.message("sbt.source.sets")

  override def getVariants(directory: PsiDirectory): util.Collection[Variant] = {
    val project = directory.getProject

    val module = ProjectFileIndex.getInstance(project).getModuleForFile(directory.getVirtualFile)
    if (module == null)
      return emptyList()

    val moduleProperties = ExternalSystemModulePropertyManager.getInstance(module)
    if (SbtProjectSystem.Id.getId != moduleProperties.getExternalSystemId)
      return emptyList()

    val result = new mutable.ArrayBuffer[Variant]()

    def addAllPaths(rootData: ContentRootData, sourceType: ExternalSystemSourceType, rootType: JpsModuleSourceRootType[_]): Seq[Variant] = {
      val paths = rootData.getPaths(sourceType)
      paths.asScala.map(p => new Variant(p.getPath, rootType)).toSeq
    }

    val contentRootsData = getModuleContentRootsData(module)
    for {
      rootData <- contentRootsData
    } {
      result ++= addAllPaths(rootData, ExternalSystemSourceType.SOURCE, JavaSourceRootType.SOURCE)
      result ++= addAllPaths(rootData, ExternalSystemSourceType.TEST, JavaSourceRootType.TEST_SOURCE)
      result ++= addAllPaths(rootData, ExternalSystemSourceType.RESOURCE, JavaResourceRootType.RESOURCE)
      result ++= addAllPaths(rootData, ExternalSystemSourceType.TEST_RESOURCE, JavaResourceRootType.TEST_RESOURCE)
    }

    result.asJava
  }
}

object SbtDirectoryCompletionContributor {

  private def getModuleContentRootsData(module: Module): Seq[ContentRootData] =
    findSbtModuleData(module) match {
      case Some(moduleData) =>
        val contentRoots = ExternalSystemApiUtil.findAll(moduleData, ProjectKeys.CONTENT_ROOT)
        contentRoots.asScala.map(_.getData).toSeq
      case None =>
        Nil
    }

  private def findSbtModuleData(module: Module): Option[DataNode[ModuleData]] = {
    val projectPath = ExternalSystemApiUtil.getExternalProjectPath(module)
    if (projectPath == null) None else {
      val project = module.getProject
      findSbtModuleData(project, projectPath)
    }
  }

  private def findSbtModuleData(project: Project, projectPath: String): Option[DataNode[ModuleData]] =
    Option(ExternalSystemApiUtil.findModuleNode(project, SbtProjectSystem.Id, projectPath))


  //NOTE: in Gradle version the utilities from this companion object are located in GradleContentRootContributor.
  //I didn't find a reason why would we need same analogue (SbtContentRootContributor).
  //Seems that it's effectively used only in Kotlin plugin and it's not clear how exactly.
  //For us it's enough to have just completion contributor
  /*
  class SbtContentRootContributor extends ExternalSystemContentRootContributor {
    override def isApplicable(systemId: String): Boolean =
      systemId == SbtProjectSystem.Id.getId

    override def findContentRoots(
      module: Module,
      sourceTypes: util.Collection[_ <: ExternalSystemSourceType]
    ): util.Collection[ExternalContentRoot] = {
      val contentRoots: Seq[ExternalContentRoot] = for {
        contentRootData <- getModuleContentRootsData(module)
        sourceType <- sourceTypes.asScala
        path <- contentRootData.getPaths(sourceType).asScala
      } yield new ExternalContentRoot(Paths.get(path.getPath), sourceType)

      contentRoots.asJava
    }
  }
  */
}