package org.jetbrains.plugins.scala.actions

import com.intellij.ide.actions.CreateDirectoryCompletionContributor
import com.intellij.ide.actions.CreateDirectoryCompletionContributor.Variant
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.model.project.{ContentRootData, ExternalSystemSourceType, ModuleData}
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys, ProjectSystemId}
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiDirectory
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.java.{JavaResourceRootType, JavaSourceRootType}
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.plugins.scala.actions.ScalaDirectoryCompletionContributorBase.getModuleContentRootsData
import org.jetbrains.plugins.scala.util.ExternalSystemUtil
import org.jetbrains.sbt.Sbt.SbtModuleChildKeyInstance
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.module.SbtSourceSetData

import java.util
import java.util.Collections.emptyList
import scala.collection.mutable
import scala.jdk.CollectionConverters.{BufferHasAsJava, CollectionHasAsScala}

/**
 * Inspired by `org.jetbrains.plugins.gradle.GradleDirectoryCompletionContributor`
 *
 * NOTE: currently there is no way to apply custom sorting to the completion items, see IDEA-306694
 */
@ApiStatus.Internal
@ApiStatus.Experimental
abstract class ScalaDirectoryCompletionContributorBase(projectSystemId: ProjectSystemId)
  extends CreateDirectoryCompletionContributor {

  override def getVariants(directory: PsiDirectory): util.Collection[Variant] = {
    val project = directory.getProject

    val module = ProjectFileIndex.getInstance(project).getModuleForFile(directory.getVirtualFile)
    if (module == null)
      return emptyList()

    val moduleProperties = ExternalSystemModulePropertyManager.getInstance(module)
    if (projectSystemId.getId != moduleProperties.getExternalSystemId)
      return emptyList()

    val result = new mutable.ArrayBuffer[Variant]()

    def addAllPaths(rootData: ContentRootData, sourceType: ExternalSystemSourceType, rootType: JpsModuleSourceRootType[_]): Seq[Variant] = {
      val paths = rootData.getPaths(sourceType)
      paths.asScala.map(p => new Variant(p.getPath, rootType)).toSeq
    }

    val contentRootsData = getModuleContentRootsData(module, projectSystemId)
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

object ScalaDirectoryCompletionContributorBase {

  private def getModuleContentRootsData(module: Module, projectSystemId: ProjectSystemId): Seq[ContentRootData] =
     findModuleData(module, projectSystemId) match {
      case Some(moduleData) =>
        val sbtSourcesSetData = ExternalSystemApiUtil.findAll(moduleData, SbtSourceSetData.Key).asScala.toSeq
        val contentRoots = (sbtSourcesSetData :+ moduleData).flatMap(ExternalSystemApiUtil.findAll(_, ProjectKeys.CONTENT_ROOT).asScala)
        contentRoots.map(_.getData)
      case None =>
        Nil
    }

  private def findModuleData(module: Module, projectSystemId: ProjectSystemId): Option[DataNode[_<:ModuleData]] = {
    val moduleId = ExternalSystemApiUtil.getExternalProjectId(module)
    if (moduleId == null) None
    else {
      getModuleDataBasedOnProjectSystemId(projectSystemId, module)
    }
  }

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

  private def getModuleDataBasedOnProjectSystemId(projectSystemId: ProjectSystemId, module: Module): Option[DataNode[_ <: ModuleData]]  = {
    val project = module.getProject
    val moduleId = ExternalSystemApiUtil.getExternalProjectId(module)
    if (moduleId == null) return None
    val rootProjectPath = Option(ExternalSystemApiUtil.getExternalRootProjectPath(module))
    if (projectSystemId == SbtProjectSystem.Id) {
      ExternalSystemUtil.getModuleDataNode(projectSystemId, project, moduleId, rootProjectPath, Some(SbtModuleChildKeyInstance))
    } else {
      ExternalSystemUtil.getModuleDataNode(projectSystemId, project, moduleId, rootProjectPath, None)
    }
  }
}