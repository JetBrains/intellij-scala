package org.jetbrains.sbt.project

import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.plugins.scala.extensions.RichFile
import org.jetbrains.sbt.project.SbtProjectResolver.{CompileScope, ImportContext, IntegrationTestScope, TestScope}
import org.jetbrains.sbt.project.data.ContentRootNode
import org.jetbrains.sbt.structure.DirectoryData
import org.jetbrains.sbt.{Sbt, SbtUtil, structure => sbtStructure}

import java.io.File
import scala.collection.mutable
import scala.jdk.CollectionConverters.CollectionHasAsScala

/**
 * This trait provides utility methods for creating content root nodes, applicable to both "legacy" and main/test module modes.
 */
trait ContentRootsResolution { self: ExternalSourceRootResolution =>

  // ! For legacy mode

  protected def createLegacyContentRoot(project: sbtStructure.ProjectData)(implicit context: ImportContext): ContentRootNode = {
    val contentRootNode = new ContentRootNode(project.base.path)
    storeExcludedPathsInContentRoot(contentRootNode, project)

    val productionSources = getProductionSources(project)
    val productionResources = getProductionResources(project)
    val testSources = getTestSources(project)
    val testResources = getTestResources(project)

    contentRootNode.storePaths(ExternalSystemSourceType.SOURCE, unmanagedDirectories(productionSources))
    contentRootNode.storePaths(ExternalSystemSourceType.SOURCE_GENERATED, managedDirectories(productionSources))
    contentRootNode.storePaths(ExternalSystemSourceType.RESOURCE, unmanagedDirectories(productionResources))
    contentRootNode.storePaths(ExternalSystemSourceType.RESOURCE_GENERATED, managedDirectories(productionResources))

    contentRootNode.storePaths(ExternalSystemSourceType.TEST, unmanagedDirectories(testSources))
    contentRootNode.storePaths(ExternalSystemSourceType.TEST_GENERATED, managedDirectories(testSources))
    contentRootNode.storePaths(ExternalSystemSourceType.TEST_RESOURCE, unmanagedDirectories(testResources))
    contentRootNode.storePaths(ExternalSystemSourceType.TEST_RESOURCE_GENERATED, managedDirectories(testResources))

    contentRootNode
  }

  // ! For main/test modules mode

  protected def createParentContentRoot(projectData: sbtStructure.ProjectData)(implicit context: ImportContext): ContentRootNode = {
    val contentRootNode = new ContentRootNode(projectData.base.path)
    storeExcludedPathsInContentRoot(contentRootNode, projectData)
    contentRootNode
  }

  private case class ExternalSystemSourceData(projectData: sbtStructure.ProjectData, path: String, sourceType: ExternalSystemSourceType)

  /**
   * Represents resolved source roots and base directories for both main and test scope in a project.
   *  - `source roots` are directories containing source files (e.g., `src/main/scala`, `src/test/scala`).
   *  - `source base directories` are base paths derived from the `sourceDirectory` key (e.g., `src/main`, `src/test`).
   *     For more details, check `org.jetbrains.sbt.structure.ProjectData#mainSourceDirectories()`.
   *
   * @param canCreateParentContentRoot indicates whether the project root directory is unique and content roots can be created for the parent module.
   *                                   This needs to be kept because multiple sbt projects can share the same base directory (e.g., in the scala3 repository).
   *
   */
  protected case class ProjectSourcesDetails(
    mainSourceRoots: Seq[(String, ExternalSystemSourceType)],
    testSourceRoots: Seq[(String, ExternalSystemSourceType)],
    mainSourceBaseDirectories: Seq[String],
    testSourceBaseDirectories: Seq[String],
    canCreateParentContentRoot: Boolean
  )

  object ProjectSourcesDetails {
    def default: ProjectSourcesDetails = ProjectSourcesDetails(Seq.empty, Seq.empty, Seq.empty, Seq.empty, canCreateParentContentRoot = true)
  }

  def resolveProjectsSourcesDetails(
    projectsGrouped: Seq[BuildProjectsGroup],
    groupedSharedRoots: Seq[SharedSourcesGroup]
  )(implicit context: ImportContext): Map[sbtStructure.ProjectData, ProjectSourcesDetails] = {
    val projects = projectsGrouped.flatMap(group => group.projects :+ group.rootProject)

    val sharedSourcesPaths = getSharedSourcesPath(groupedSharedRoots)
    val allExternalSystemSources = projects.flatMap { project =>
      val mainSources = resolveExternalSystemSources(isMainScope = true, project, sharedSourcesPaths)
      val testSources = resolveExternalSystemSources(isMainScope = false, project, sharedSourcesPaths)
      mainSources ++ testSources
    }

    val sortedSources = sortSourcesByType(allExternalSystemSources)
    val uniqueSources = sortedSources.distinctBy(_.path)

    val uniqueSourcesPaths = uniqueSources.map(_.path)
    val projectToSources = uniqueSources.groupBy(_.projectData)

    // If the shared sources group is derived from the standard bases, content roots will be created
    // for the group base and the group base with `src/main` and `src/test` suffixes
    // (see ExternalSourceRootResolution.createSharedSourceSetModule and ExternalSourceRootResolution.createParentSharedSourcesModule).
    // It is important to gather the content root paths here to prevent creating duplicate content roots.
    // * Additionally, content roots may be created for individual source directories (see ContentRootsResolution.createContentRootNodes).
    // However, any overlap with individual source directories for shared sources is handled in #resolveExternalSystemSources.
    val sharedSourcesBaseDirs = groupedSharedRoots.filter(_.hasStandardBasePath).flatMap { group =>
      val mainBase = group.base / "src" / "main"
      val testBase = group.base / "src" / "test"
      val actualBases = Seq(mainBase, testBase).filter(base => group.sourceRoots.exists(_.directory.isUnder(base)))
      group.base +: actualBases
    }.map(SbtUtil.normalizePath)

    // The mainSourceDirectories/testSourceDirectories values are derived from the sourceDirectory sbt key.
    // In the ideal/default case, for example, the mainSourceDirectories value is src/main, and it contains source paths like scala, java, etc.
    // However, users might modify the sourceDirectory key, making it the same as another project's source directory (as is present in https://github.com/scala/scala3)
    // or within the same project but in a different scope. This is why it's necessary to collect already reserved base source directories to prevent duplicates.
    val alreadyUsedSourceBaseDirs = mutable.HashSet.empty[String]
    // See org.jetbrains.sbt.project.ContentRootsResolution.ProjectSourcesDetails.canCreateParentContentRoot
    val alreadyUsedProjectBaseDirs = mutable.HashSet.empty[File]
    projects.map { project =>
      val sources = projectToSources.getOrElse(project, Seq.empty)

      def getSourceRoots(sourceTypeFilter: ExternalSystemSourceType => Boolean): Seq[(String, ExternalSystemSourceType)] =
        sources.filter(source => sourceTypeFilter(source.sourceType)).map { case ExternalSystemSourceData(_, path, sourceType) => (path, sourceType) }

      val mainSources = getSourceRoots(s => !s.isTest && !s.isExcluded)
      val testSources = getSourceRoots(_.isTest)

      def getValidSourceBaseDirs(sourceBaseDirs: Seq[File]): Seq[String] =
        sourceBaseDirs.map(SbtUtil.normalizePath)
          .filterNot(uniqueSourcesPaths.contains)
          .filterNot(alreadyUsedSourceBaseDirs.contains)
          .filterNot(sharedSourcesBaseDirs.contains)

      val mainSourceBaseDirs = getValidSourceBaseDirs(project.mainSourceDirectories)
      alreadyUsedSourceBaseDirs ++= mainSourceBaseDirs
      val testSourceBaseDirs = getValidSourceBaseDirs(project.testSourceDirectories)
      alreadyUsedSourceBaseDirs ++= testSourceBaseDirs

      val isProjectDirReserved = alreadyUsedProjectBaseDirs.contains(project.base)
      alreadyUsedProjectBaseDirs += project.base

      project -> ProjectSourcesDetails(mainSources, testSources, mainSourceBaseDirs, testSourceBaseDirs, !isProjectDirReserved)
    }.toMap
  }

  /**
   * Sorts sources based on [[ExternalSystemSourceType]] in a defined and predictable order.
   * This is necessary when the same directory within a single project is defined with multiple types,
   * such as being both a SOURCE and a RESOURCE type.
   *
   * Ensuring a consistent order prevents scenarios where, during one reload, the directory is assigned one type,
   * and during a subsequent reload, it's assigned another type.
   *
   * Anyway, I think it's a bit of an odd case if the same path is added twice with different types, and it's not entirely clear which type it should have
   * (I don’t exclude the possibility of changes to this sorting if a real use case arises; for now it's only theoretical speculation).
   *
   * @note In [[com.intellij.openapi.externalSystem.service.project.manage.ContentRootDataService#importData]],
   *       within each content root, source paths are processed in the order defined by the enums in [[ExternalSystemSourceType]].
   *       SOURCE and TEST types have special handling:
   *           - If the same path exists with a different type (e.g., RESOURCE), its type won’t override SOURCE or TEST.
   *           - If the same path exists as SOURCE_GENERATED or TEST_GENERATED, the type is updated to the generated one.
   *
   *       All other paths are processed in the enum-defined order, with types overriding each other.
   *       For example, if a path X has both RESOURCE and TEST_RESOURCE types, it will be added as TEST_RESOURCE, as it is processed after RESOURCE.
   */
  private def sortSourcesByType(sources: Seq[ExternalSystemSourceData]): Seq[ExternalSystemSourceData] = {
    val sourceTypePriorityMap = ExternalSystemSourceType.values().toSeq.zipWithIndex.toMap
    sources.sortBy(source => sourceTypePriorityMap.getOrElse(source.sourceType, Int.MaxValue))
  }

  private def getSharedSourcesPath(sharedRoots: Seq[SharedSourcesGroup]): Set[String] =
    sharedRoots.flatMap(_.sourceRoots)
      .map(sr => FileUtil.toSystemIndependentName(sr.directory.getPath)).toSet

  private def resolveExternalSystemSources(
    isMainScope: Boolean,
    project: sbtStructure.ProjectData,
    sharedSourcesPaths: Set[String],
  )(implicit context: ImportContext): Seq[ExternalSystemSourceData] = {
    val sources =
      if (isMainScope) getProductionSources(project)
      else getTestSources(project)
    val resources =
      if (isMainScope) getProductionResources(project)
      else getTestResources(project)

    val (sourceType, sourceGeneratedType, resourceType, resourceGeneratedType) =
      if (isMainScope) (
        ExternalSystemSourceType.SOURCE,
        ExternalSystemSourceType.SOURCE_GENERATED,
        ExternalSystemSourceType.RESOURCE,
        ExternalSystemSourceType.RESOURCE_GENERATED
      )
      else (
        ExternalSystemSourceType.TEST,
        ExternalSystemSourceType.TEST_GENERATED,
        ExternalSystemSourceType.TEST_RESOURCE,
        ExternalSystemSourceType.TEST_RESOURCE_GENERATED
      )

    val unmanagedDirs = unmanagedDirectories(sources).filterNot(sharedSourcesPaths.contains).map((_, sourceType))
    val unmanagedResourceDirs = unmanagedDirectories(resources).filterNot(sharedSourcesPaths.contains).map((_, resourceType))

    val managedDirs = managedDirectories(sources).map((_, sourceGeneratedType))
    val managedResourceDirs = managedDirectories(resources).map((_, resourceGeneratedType))

    val allDirs = unmanagedDirs ++ unmanagedResourceDirs ++ managedDirs ++ managedResourceDirs
    allDirs.map { case (path, sourceType) => ExternalSystemSourceData(project, path, sourceType) }
  }

  /**
   * The method creates content root nodes based on the given source/resource base directories and roots.
   * It works in 2 stages:
   *  1. First, it creates a content root for each source root base directory (`sourceRootBaseDirs`)
   *  1. Then, it creates dedicated content roots for those source/resource roots that are not located inside
   *     any base directory. This is primarily used by generated sources in external directories,
   *     like  `.../target/.../src_managed/main`
   *
   * @param sourceRootBaseDirs Contains directories which can contain source/resource roots.<br>
   *                           Examples:
   *                             - `.../src/main` (can contain scala, scala-3, resources)
   *                             - `.../src/test`
   * @param sourceRoots        Contains concrete directories that serve as source/resource roots.<br>
   *                           Examples:
   *                             - `.../src/main/scala`
   *                             - `.../src/main/scala-3`
   *                             - `.../src/main/resources`
   *                             - `.../target/.../src_managed/main`
   */
  protected def createContentRootNodes(
    sourceRootBaseDirs: Seq[String],
    sourceRoots: Seq[(String, ExternalSystemSourceType)],
  ): Seq[ContentRootNode] = {
    val _sourceRootBaseDirs = sourceRootBaseDirs.map((_, None))
    val _sourceRoots = sourceRoots.map { case (path, sourceType) => (path, Some(sourceType)) }
    val sortedSources = (_sourceRootBaseDirs ++ _sourceRoots).sortBy(_._1)

    val contentRootNodes = sortedSources.foldLeft(Seq.empty[ContentRootNode]) { case (contentRootNodes, (sourceRootPath, sourceType)) =>
      val suitableContentRootNode = findContentRootContainingPath(contentRootNodes, sourceRootPath)
      suitableContentRootNode match {
        case Some(contentRootNode) if sourceType.nonEmpty =>
          contentRootNode.storePath(sourceType.get, sourceRootPath)
          contentRootNodes
        case None =>
          val node = new ContentRootNode(sourceRootPath)
          sourceType.foreach(node.storePath(_, sourceRootPath))
          contentRootNodes :+ node
        case _ => contentRootNodes
      }
    }

    contentRootNodes.filterNot(isContentRootMissingPaths)
  }

  private def isContentRootMissingPaths(contentRootNode: ContentRootNode): Boolean = {
    val allSourceTypes = ExternalSystemSourceType.values().toSeq
    !allSourceTypes.exists(sourceType => contentRootNode.data.getPaths(sourceType).asScala.nonEmpty)
  }

  private def findContentRootContainingPath(contentRoots: Seq[ContentRootNode], path: String): Option[ContentRootNode] = {
    val dir = new File(path)
    contentRoots.find { contentRoot =>
      val contentRootDir = new File(contentRoot.data.getRootPath)
      dir.isUnder(contentRootDir, strict = false)
    }
  }

  private def managedDirectories(dirs: Seq[sbtStructure.DirectoryData]): Seq[String] =
    dirs.filter(_.managed).map(_.file.canonicalPath)

  private def unmanagedDirectories(dirs: Seq[sbtStructure.DirectoryData]): Seq[String] =
    dirs.filterNot(_.managed).map(_.file.canonicalPath)

  private def getProductionSources(project: sbtStructure.ProjectData)(implicit context: ImportContext): Seq[DirectoryData] =
    validSourceRootPathsIn(project, CompileScope)(_.sources)

  private def getProductionResources(project: sbtStructure.ProjectData)(implicit context: ImportContext): Seq[DirectoryData] =
    validSourceRootPathsIn(project, CompileScope)(_.resources)

  private def getTestSources(project: sbtStructure.ProjectData)(implicit context: ImportContext): Seq[DirectoryData] =
    validSourceRootPathsIn(project, TestScope)(_.sources) ++
      validSourceRootPathsIn(project, IntegrationTestScope)(_.sources)

  private def getTestResources(project: sbtStructure.ProjectData)(implicit context: ImportContext): Seq[DirectoryData] =
    validSourceRootPathsIn(project, TestScope)(_.resources) ++
      validSourceRootPathsIn(project, IntegrationTestScope)(_.resources)

  private def validSourceRootPathsIn(
    project: sbtStructure.ProjectData,
    scope: String,
  )(
    selector: sbtStructure.ConfigurationData => Seq[sbtStructure.DirectoryData]
  )(implicit context: ImportContext): Seq[sbtStructure.DirectoryData] = {
    val configurationData = project.configurations.find(_.id == scope)
    val directoryData: Seq[DirectoryData] = configurationData.map(selector).getOrElse(Seq.empty)

    // NOTE: At least since 2015 we filtered out source roots that are outside project roots (~IntelliJ module).
    //
    // The reason was because back then IntelliJ module content root had 1-1 correspondence with the project root.
    // But IntelliJ doesn't allow registering source roots outside content roots,
    // so we had to filter out directories from outside.
    //
    // But since we introduced separate modules for main/test sources, it's no an issue anymore:
    // Separate content roots are registered for all source/resource root dirs
    // (e.g., src/main or target/.../src_managed/main)
    // Source directories will be added to proper content roots and won't be outside them
    // (see logic in `createContentRootNodes`)
    if (context.useSeparateProdTestSources)
      directoryData
    else
      directoryData.filterNot(_.file.isOutsideOf(project.base))
  }


  protected def storeExcludedPathsInContentRoot(
    contentRoot: ContentRootNode,
    project: sbtStructure.ProjectData,
  )(implicit context: ImportContext): Unit = {
    val extractedExcludes = project.configurations.flatMap(_.excludes)

    val excludedDirs = if (extractedExcludes.nonEmpty)
      extractedExcludes.distinct
    else if (context.sbtVersion.isSbt2) {
      // NOTE Since sbt 2.0 there is only one target dir in the build root and it's hardcoded as "target"
      // We also hardcode it, but we add the directory to every sbt sub-project to make the migration from 1.x to 2.x easier.
      // (It would be nice if IntelliJ excluded existing target directories with cache files from sbt 1.x)
      // - https://github.com/sbt/sbt/issues/3681 (it's WIP currently, 10 Feb 2025)
      // - https://github.com/sbt/sbt/issues/8037
      //
      // We could extract this hardcoding logic to sbt-structure plugin, but for now it seems not necessary
      Seq(new File(contentRoot.data.getRootPath, Sbt.TargetDirectory))
    } else
      Seq(project.target)

    excludedDirs.foreach { path =>
      contentRoot.storePath(ExternalSystemSourceType.EXCLUDED, path.path)
    }
  }
}
