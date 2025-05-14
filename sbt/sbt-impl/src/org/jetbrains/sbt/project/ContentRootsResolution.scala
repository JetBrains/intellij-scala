package org.jetbrains.sbt.project

import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.plugins.scala.extensions.RichFile
import org.jetbrains.sbt.project.SbtProjectResolver.{CompileScope, ImportContext, IntegrationTestScope, TestScope}
import org.jetbrains.sbt.project.data.ContentRootNode
import org.jetbrains.sbt.structure.DirectoryData
import org.jetbrains.sbt.{Sbt, structure => sbtStructure}

import java.io.File
import scala.collection.mutable

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
   */
  protected case class ProjectSourcesDetails(
    mainSourceRoots: Seq[(String, ExternalSystemSourceType)],
    testSourceRoots: Seq[(String, ExternalSystemSourceType)],
    mainSourceBaseDirectories: Seq[String],
    testSourceBaseDirectories: Seq[String]
  )

  object ProjectSourcesDetails {
    def default: ProjectSourcesDetails = ProjectSourcesDetails(Seq.empty, Seq.empty, Seq.empty, Seq.empty)
  }

  def resolveProjectsSourcesDetails(
    projectsGrouped: Seq[BuildProjectsGroup],
    groupedSharedRoots: Seq[SharedSourcesGroup]
  )(implicit context: ImportContext): Map[sbtStructure.ProjectData, ProjectSourcesDetails] = {
    val projects = projectsGrouped.flatMap(group => group.projects :+ group.rootProject)

    val sharedSourceRoots = getSharedSourceRoots(groupedSharedRoots)
    val allExternalSystemSources = projects.flatMap { project =>
      val mainSources = resolveExternalSystemSources(isMainScope = true, project, sharedSourceRoots)
      val testSources = resolveExternalSystemSources(isMainScope = false, project, sharedSourceRoots)
      mainSources ++ testSources
    }

    /*
    In ContentRootDataService#importData, within each content root, source paths are processed in the order defined by the enums in ExternalSystemSourceType.
    SOURCE and TEST types have special handling:
     - If the same path exists with a different type (e.g., RESOURCE), its type won’t override SOURCE or TEST.
     - If the same path exists as SOURCE_GENERATED or TEST_GENERATED, the type is updated to the generated one.

    All other paths are processed in the enum-defined order, with types overriding each other.
    For example, if a path X has both RESOURCE and TEST_RESOURCE types, it will be added as TEST_RESOURCE, as it is processed after RESOURCE.

    Anyway, I think it's a bit of an odd case if the same path is added twice with different types, and it's not entirely clear which type it should have.
    So I implemented it in a way that SOURCE and TEST types always take precedence by being at the top of the order.The remaining source types are handled in a random order.
    (I don’t exclude the possibility of changes to this sorting if a real use case arises; for now it's only theoretical speculation)
    */
    val sourceTypePriorityMap = Seq(ExternalSystemSourceType.SOURCE, ExternalSystemSourceType.TEST).zipWithIndex.toMap
    val sortedSources = allExternalSystemSources.sortBy(source => sourceTypePriorityMap.getOrElse(source.sourceType, Int.MaxValue))
    val uniqueSources = sortedSources.distinctBy(_.path)

    val uniqueSourcesPaths = uniqueSources.map(_.path)
    val projectToSources = uniqueSources.groupBy(_.projectData)

    // In shared source modules, content roots will be created for the group base and the group base with `src/main` and `src/test` suffixes
    // (see ExternalSourceRootResolution.createSharedSourceSetModule and ExternalSourceRootResolution.createParentSharedSourcesModule).
    // It is important to gather the content root paths here to prevent creating duplicate content roots.
    // * Additionally, content roots may be created for individual source directories (see ContentRootsResolution.createContentRootNodes).
    // However, any overlap with individual source directories for shared sources is handled in #resolveExternalSystemSources.
    val sharedSourcesBaseDirs = groupedSharedRoots.flatMap { group =>
      val base = group.base.path
      Seq(base, s"$base/src/main", s"$base/src/test")
    }

    // The mainSourceDirectories/testSourceDirectories values are derived from the sourceDirectory sbt key.
    // In the ideal/default case, for example, the mainSourceDirectories value is src/main, and it contains source paths like scala, java, etc.
    // However, users might modify the sourceDirectory key, making it the same as another project's source directory (as is present in https://github.com/scala/scala3)
    // or within the same project but in a different scope. This is why it's necessary to collect already reserved base source directories to prevent duplicates.
    //
    // It might be worth considering creating a shared sources module when source base directories are duplicated across multiple projects,
    // and these directories are located within each project's root. Currently, shared sources modules are created only for roots located outside of the project root.
    // See https://youtrack.jetbrains.com/issue/SCL-23867/The-project-doesnt-compile-when-there-is-a-shared-directory-between-2-modules
    val alreadyUsedSourceBaseDirs = mutable.HashSet.empty[String]
    projects.map { project =>
      val sources = projectToSources.getOrElse(project, Seq.empty)

      def getSourceRoots(sourceTypeFilter: ExternalSystemSourceType => Boolean): Seq[(String, ExternalSystemSourceType)] =
        sources.filter(source => sourceTypeFilter(source.sourceType)).map { case ExternalSystemSourceData(_, path, sourceType) => (path, sourceType) }

      val mainSources = getSourceRoots(s => !s.isTest && !s.isExcluded)
      val testSources = getSourceRoots(_.isTest)

      def getValidSourceBaseDirs(sourceBaseDirs: Seq[File]): Seq[String] =
        sourceBaseDirs.map(_.path)
          .filterNot(uniqueSourcesPaths.contains)
          .filterNot(alreadyUsedSourceBaseDirs.contains)
          .filterNot(sharedSourcesBaseDirs.contains)

      val mainSourceBaseDirs = getValidSourceBaseDirs(project.mainSourceDirectories)
      alreadyUsedSourceBaseDirs ++= mainSourceBaseDirs
      val testSourceBaseDirs = getValidSourceBaseDirs(project.testSourceDirectories)
      alreadyUsedSourceBaseDirs ++= testSourceBaseDirs

      project -> ProjectSourcesDetails(mainSources, testSources, mainSourceBaseDirs, testSourceBaseDirs)
    }.toMap
  }

  private def getSharedSourceRoots(sharedRoots: Seq[SharedSourcesGroup]): Map[SourceRoot.Scope, Map[SourceRoot.Kind, Set[String]]] =
    sharedRoots.flatMap(_.sourceRoots)
      .groupBy(_.scope)
      .view.mapValues(_.groupBy(_.kind).view.mapValues(_.map(sr => FileUtil.toSystemIndependentName(sr.directory.getPath)).toSet).toMap)
      .toMap

  private def resolveExternalSystemSources(
    isMainScope: Boolean,
    project: sbtStructure.ProjectData,
    sharedSourceRoots:  Map[SourceRoot.Scope, Map[SourceRoot.Kind, Set[String]]],
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

    val sharedSourceRootsInScope =
      if (isMainScope) sharedSourceRoots.getOrElse(SourceRoot.Scope.Compile, Map.empty)
      else sharedSourceRoots.getOrElse(SourceRoot.Scope.Test, Map.empty)

    val sharedSources = sharedSourceRootsInScope.getOrElse(SourceRoot.Kind.Sources, Set.empty)
    val sharedResources = sharedSourceRootsInScope.getOrElse(SourceRoot.Kind.Resources, Set.empty)

    val unmanagedDirs = unmanagedDirectories(sources).filterNot(sharedSources.contains).map((_, sourceType))
    val unmanagedResourceDirs = unmanagedDirectories(resources).filterNot(sharedResources.contains).map((_, resourceType))

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
    val contentRootsForSourceBaseDirs = sourceRootBaseDirs.distinct.map(new ContentRootNode(_))
    sourceRoots.foldLeft(contentRootsForSourceBaseDirs) { case (contentRootNodes, (sourceRootPath, sourceType)) =>
      val suitableContentRootNode = findContentRootContainingPath(contentRootNodes, sourceRootPath)
      suitableContentRootNode match {
        case Some(contentRootNode) =>
          contentRootNode.storePath(sourceType, sourceRootPath)
          contentRootNodes
        case None =>
          val node = new ContentRootNode(sourceRootPath)
          node.storePath(sourceType, sourceRootPath)
          contentRootNodes :+ node
        case _ => contentRootNodes
      }
    }
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
