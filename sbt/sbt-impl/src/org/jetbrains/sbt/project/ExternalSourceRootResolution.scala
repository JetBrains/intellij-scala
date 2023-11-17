package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.model.project.{ExternalSystemSourceType, ModuleData}
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.roots.DependencyScope
import org.jetbrains.plugins.scala.extensions.RichFile
import org.jetbrains.sbt.project.data._
import org.jetbrains.sbt.project.sources.SharedSourcesModuleType
import org.jetbrains.sbt.structure.{ProjectData, ProjectDependencyData}
import org.jetbrains.sbt.{structure => sbtStructure}

import java.io.File
import java.net.URI

trait ExternalSourceRootResolution { self: SbtProjectResolver =>
  def createSharedSourceModules(
    projectToModuleNode: Map[sbtStructure.ProjectData, Node[_ <: ModuleData]],
    libraryNodes: Seq[LibraryNode],
    moduleFilesDirectory: File,
    insertProjectTransitiveDependencies: Boolean,
    shouldGroupModulesFromSameBuild: Boolean,
    buildProjectsGroups: Seq[BuildProjectsGroup]
  ): Seq[Node[_ <: ModuleData]] = {
    val projects = projectToModuleNode.keys.toSeq
    val sharedRoots = sharedAndExternalRootsIn(projects)
    val grouped = groupSharedRoots(sharedRoots)
    grouped.map { group =>
      createSourceModuleNodesAndDependencies(
        group,
        projectToModuleNode,
        libraryNodes,
        moduleFilesDirectory,
        insertProjectTransitiveDependencies,
        shouldGroupModulesFromSameBuild,
        buildProjectsGroups
      )
    }
  }

  protected def createModuleDependencies(
    projectDependencies: Seq[ProjectDependencyData],
    allModules: Seq[Node[_ <: ModuleData]],
    moduleNode: Node[_ <: ModuleData],
    insertProjectTransitiveDependencies: Boolean
  ): Unit = {
    projectDependencies.foreach { dependencyId =>
      val dependency = allModules
        .find(_.getId == ModuleNode.combinedId(dependencyId.project, dependencyId.buildURI))
        .getOrElse(throw new ExternalSystemException("Cannot find project dependency: " + dependencyId.project))

      val dependencyNode = new ModuleDependencyNode(moduleNode, dependency)
      dependencyNode.setScope(scopeFor(dependencyId.configurations.distinct))
      val exported = if (insertProjectTransitiveDependencies) false else true
      dependencyNode.setExported(exported)
      moduleNode.add(dependencyNode)
    }
  }

  private def createSourceModuleNodesAndDependencies(
    rootGroup: RootGroup,
    projectToModuleNode: Map[sbtStructure.ProjectData, Node[_ <: ModuleData]],
    libraryNodes: Seq[LibraryNode],
    moduleFilesDirectory: File,
    insertProjectTransitiveDependencies: Boolean,
    shouldGroupModulesFromSameBuild: Boolean,
    buildProjectsGroups: Seq[BuildProjectsGroup]
  ): Node[_ <: ModuleData] = {
    val projects = rootGroup.projects

    val sourceModuleNode = {
      val (moduleNode, contentRootNode) = createSourceModule(rootGroup, moduleFilesDirectory, shouldGroupModulesFromSameBuild)
      //todo: get jdk from a corresponding jvm module ?
      moduleNode.add(ModuleSdkNode.inheritFromProject)

      // Select a single project and clone its module / project dependencies.
      // It seems that dependencies of any single project should be enough to highlight files in the shared source module.
      // Please note that we mix source modules into other modules on compilation,
      // so source module dependencies are not relevant for compilation, only for highlighting.
      val representativeProject = representativeProjectIn(rootGroup.projects)
      moduleNode.add(createScalaSdkData(representativeProject.scala))

      val representativeProjectDependencies = representativeProject.dependencies

      //add library dependencies of the representative project
      val libraryDependencies = representativeProjectDependencies.modules
      moduleNode.addAll(createLibraryDependencies(libraryDependencies)(moduleNode, libraryNodes.map(_.data)))

      //add unmanaged jars/libraries dependencies of the representative project
      val unmanagedLibraryDependencies = representativeProjectDependencies.jars
      moduleNode.addAll(createUnmanagedDependencies(unmanagedLibraryDependencies)(moduleNode))

      //add project dependencies of the representative project
      createModuleDependencies(representativeProjectDependencies.projects, projectToModuleNode.values.toSeq, moduleNode, insertProjectTransitiveDependencies)

      //add some managed sources of the representative project
      //(see description of `getManagedSourceRootsFromRepresentativeProjectToIncludeAsBaseModelSourceRoots` method for the details)
      val representativeProjectManagedSources = getManagedSourceRootsFromRepresentativeProjectToIncludeAsBaseModelSourceRoots(rootGroup, representativeProject)
      representativeProjectManagedSources.foreach { root =>
        val esSourceType = calculateEsSourceType(root)
        contentRootNode.storePath(esSourceType, root.directory.path)
      }

      projectToModuleNode.get(representativeProject).foreach { reprProjectModule =>
        //put source module to the same module group
        val reprProjectModulePrefix = Option(reprProjectModule.getInternalName.stripSuffix(reprProjectModule.getModuleName))
        val moduleNameWithGroupName = prependModuleNameWithGroupName(moduleNode.getInternalName, reprProjectModulePrefix)
        moduleNode.setInternalName(moduleNameWithGroupName)
        if (shouldGroupModulesFromSameBuild) {
          //find rootNode for reprProjectModule, because shared sources module should be put in the same root
          val rootNode = findRootNodeForProjectData(representativeProject, buildProjectsGroups, projectToModuleNode)
          rootNode.foreach(_.add(moduleNode))
        }
      }

      moduleNode
    }

    val dependentModulesThatRequireSharedSourcesModule = if (insertProjectTransitiveDependencies) {
      getAllModulesThatRequireSharedSourcesModule(projectToModuleNode, projects)
    } else {
      Seq.empty
    }

    //add shared sources module as a dependency to platform modules
    val sharedSourceRootProjects = projects.map(projectToModuleNode).map((_, DependencyScope.COMPILE))
    val allModulesThatRequireSharedSourcesModule = sharedSourceRootProjects ++ dependentModulesThatRequireSharedSourcesModule
    allModulesThatRequireSharedSourcesModule.foreach { case (ownerModule, dependencyScope) =>
      val node = new ModuleDependencyNode(ownerModule, sourceModuleNode)
      node.setScope(dependencyScope)
      node.setExported(true)
      ownerModule.add(node)
    }

    sourceModuleNode
  }

  private def findRootNodeForProjectData(
    representativeProject: ProjectData,
    buildProjectsGroups: Seq[BuildProjectsGroup],
    projectToModuleNode: Map[sbtStructure.ProjectData, Node[_ <: ModuleData]]
  ): Option[Node[_<:ModuleData]] = {
    val rootProjectDataOpt = buildProjectsGroups
      .find(_.projects.contains(representativeProject))
      .map(_.rootProject)
    rootProjectDataOpt.flatMap(projectToModuleNode.get)
  }

  /**
   * if project transitive dependencies feature is on, it is required to put shared sources module not only in it's owner module (module with shared sources),
   * but in all modules which depend on modules that have shared resources
   */
  private def getAllModulesThatRequireSharedSourcesModule(
    projectToModuleNode: Map[sbtStructure.ProjectData, Node[_ <: ModuleData]],
    sharedSourcesProjects: Seq[ProjectData]
  ): Seq[(Node[_ <: ModuleData], DependencyScope)] = {
    projectToModuleNode
      .filterNot { case (project, _) => sharedSourcesProjects.contains(project) }
      .flatMap { case (project, moduleNode) =>
        val projectsDependentOnSharedSourceProjects = for {
          projectDependencyData <- project.dependencies.projects
          sharedSourcesProjectData <- sharedSourcesProjects
          isTheSameSbtProject = Option(sharedSourcesProjectData.buildURI) == projectDependencyData.buildURI
          if  isTheSameSbtProject && sharedSourcesProjectData.id == projectDependencyData.project
        } yield projectDependencyData
        Option(projectsDependentOnSharedSourceProjects).filter(_.nonEmpty)
          .map { dependency => (moduleNode, scopeFor(dependency.flatMap(_.configurations))) }
      }.toSeq
  }

  /**
   * Selects an arbitrary project, preferable a JVM one
   *
   * Also see [[org.jetbrains.plugins.scala.project.ModuleExt.findRepresentativeModuleForSharedSourceModule]]
   */
  private def representativeProjectIn(projects: Seq[ProjectData]): ProjectData = {
    val isNonJvmTitle = (title: String) => {
      val titleLower = title.toLowerCase()
      titleLower.endsWith("js") || titleLower.endsWith("native")
    }

    val isNonJvmProject = (project: ProjectData) =>
      isNonJvmTitle(project.id) || isNonJvmTitle(project.name)

    //We sort projects by name to have a more deterministic way of how representative projects are picked in cross-build projects
    //If we don't do that, different projects might have dependencies on representative projects with different scala version
    //NOTE: we assume that all subprojects have same prefix and are only different in the suffix
    val projectsSorted = projects.sortBy(_.id)
    val (nonJvmProjects, jvmProjects) = projectsSorted.partition(isNonJvmProject)
    if (jvmProjects.nonEmpty)
      jvmProjects.head
    else
      nonJvmProjects.head
  }

  private def createSourceModule(
    group: RootGroup,
    moduleFilesDirectory: File,
    shouldGroupModulesFromSameBuild: Boolean
  ): (Node[_ <: ModuleData], ContentRootNode) = {
    val groupBase = group.base
    val moduleNode = createModuleNode(
      SharedSourcesModuleType.instance.getId,
      group.name,
      group.name,
      moduleFilesDirectory.path,
      groupBase.canonicalPath,
      shouldGroupModulesFromSameBuild
    )

    val contentRootNode = new ContentRootNode(groupBase.path)
    group.roots.foreach { root =>
      val esSourceType = calculateEsSourceType(root)
      contentRootNode.storePath(esSourceType, root.directory.path)
    }

    moduleNode.add(contentRootNode)

    setupOutputDirectories(moduleNode, contentRootNode)

    (moduleNode, contentRootNode)
  }


  /**
   * The primary use case for this logic is to handle SBT projects with `projectmatrix` sbt plugin.<br>
   * You can inspect `sbt-projectmatrix-with-source-generators` test project as an example.
   *
   * Details:<br>
   * In sbt build with `projectmatrix` sbt plugin, for a single project multiple subprojects are generated
   * For example if we define single project {{{
   *     val downstream = (projectMatrix in file("downstream"))
   *         .settings(commonSettings(false) *)
   *         .jvmPlatform(scalaVersions = Seq("2.12.17", "2.13.10"))
   *         .jsPlatform(scalaVersions = Seq("2.12.17", "2.13.10"))
   * }}}
   * 4 extra subprojects will be generated (2 JVM projects with 2 scala versions and 2 JS projects with 2 scala version)
   *
   * But generated sources for such projects will be located outside their base directory (or "contentRoot" in terms of IDEA)
   * Instead, they will be located in the content root of the original project, but in a special folders, like: {{{
   *     target/jvm-2.12/src_managed/main
   *     target/jvm-2.13/src_managed/main
   *     target/js-2.12/src_managed/main
   *     target/js-2.13/src_managed/main
   * }}}
   * So they will not be registered as source roots for IntelliJ Module (source roots must be located under the content root).
   * That's why we need to explicitly add source dependency from the representative project, by analogy with it's module/library/jars dependencies
   *
   * In case some logic is not clear, try to comment it out and run project structure/highlighting tests
   */
  private def getManagedSourceRootsFromRepresentativeProjectToIncludeAsBaseModelSourceRoots(
    rootGroup: RootGroup,
    representativeProject: ProjectData
  ): Set[Root] = {
    val rootGroupBase = rootGroup.base
    val representativeProjectBase = representativeProject.base

    val sourceRootsFromRepresentative: Seq[Root] = sourceRootsIn(representativeProject)
    sourceRootsFromRepresentative
      .filter(_.managed)
      .toSet
      //ensure that source roots are not already listed in root group roots to avoid duplicates
      .diff(rootGroup.roots.toSet)
      //ensure that source roots are in the content root of base module
      .filter(_.directory.isUnder(rootGroupBase))
      //get those source roots which are outside representative project content root
      .filterNot(_.directory.isUnder(representativeProjectBase))
  }

  //target directory are expected by jps compiler:
  //if they are missing all sources are marked dirty and there is no incremental compilation
  private def setupOutputDirectories(moduleNode: Node[_ <: ModuleData], contentRootNode: ContentRootNode): Unit = {
    moduleNode.setInheritProjectCompileOutputPath(false)

    val contentRoot = contentRootNode.data.getRootPath

    contentRootNode.data.storePath(ExternalSystemSourceType.EXCLUDED, getOrCreateTargetDir(contentRoot, "target").getAbsolutePath)

    moduleNode.setCompileOutputPath(ExternalSystemSourceType.SOURCE, getOrCreateTargetDir(contentRoot, "target/classes").getAbsolutePath)
    moduleNode.setCompileOutputPath(ExternalSystemSourceType.TEST, getOrCreateTargetDir(contentRoot, "target/test-classes").getAbsolutePath)
  }

  private def getOrCreateTargetDir(root: String, relPath: String): File = {
    val file = new File(root, relPath)

    if (!file.exists()) {
      FileUtilRt.createDirectory(file)
    }

    file
  }

  private def calculateEsSourceType(root: Root): ExternalSystemSourceType =
    ExternalSystemSourceType.from(
      root.scope == Root.Scope.Test,
      root.managed,
      root.kind == Root.Kind.Resources,
      false
    )

  private def sharedAndExternalRootsIn(projects: Seq[sbtStructure.ProjectData]): Seq[SharedRoot] = {
    val projectRoots = projects.flatMap(project => sourceRootsIn(project).map(ProjectRoot(project,_)))

    // TODO return the message about omitted directories
    val internalSourceDirectories = projectRoots.filter(_.isInternal).map(_.root.directory)

    projectRoots
      .filter(it => it.isExternal && !internalSourceDirectories.contains(it.root.directory))
      .groupBy(_.root)
      .view.mapValues(_.map(_.project).toSet).toMap
      .map(p => SharedRoot(p._1, p._2.toSeq))
      .toSeq
  }

  private def groupSharedRoots(roots: Seq[SharedRoot]): Seq[RootGroup] = {
    val nameProvider = new SharedSourceRootNameProvider()

    // TODO consider base/projects correspondence
    val rootsGroupedByBase = roots.groupBy(_.root.basePathFromKnownHardcodedDefaultPaths)
    rootsGroupedByBase.toList.collect {
      //NOTE: ignore roots with empty base to avoid dangling "shared-sources" module
      case (Some(base), sharedRoots) =>
        val name = nameProvider.nameFor(base)
        val projects = sharedRoots.flatMap(_.projects).distinct
        RootGroup(name, sharedRoots.map(_.root), projects)
    }
  }

  private def sourceRootsIn(project: sbtStructure.ProjectData): Seq[Root] = {
    val relevantScopes = Set("compile", "test", "it")

    val relevantConfigurations = project.configurations.filter(it => relevantScopes.contains(it.id))

    relevantConfigurations.flatMap { configuration =>
      def createRoot(kind: Root.Kind)(directory: sbtStructure.DirectoryData): Root = {
        val scope = if (configuration.id == "compile") Root.Scope.Compile else Root.Scope.Test
        Root(scope, kind, directory.file.canonicalFile, directory.managed)
      }

      val sourceRoots = configuration.sources.map(createRoot(Root.Kind.Sources))
      val resourceRoots = configuration.resources.map(createRoot(Root.Kind.Resources))
      sourceRoots ++ resourceRoots
    }
  }

  /**
   * This class is designed to group projects from single SBT build.
   * Note, SBT single sbt build can consists from multiple other builds using `ProjectRef`
   *
   * @param buildUri can point to a directory ot a github repository
   */
  protected case class BuildProjectsGroup(
    buildUri: URI,
    rootProject: ProjectData,
    projects: Seq[ProjectData],
    rootProjectModuleNameUnique: String,
  )

  private case class RootGroup(name: String, roots: Seq[Root], projects: Seq[sbtStructure.ProjectData]) {
    lazy val base: File = commonBase(roots)

    private def commonBase(roots: Seq[Root]): File = {
      import scala.jdk.CollectionConverters._
      val paths = roots.map { root =>
        root.basePathFromKnownHardcodedDefaultPaths.getOrElse(root.directory)
          .getCanonicalFile.toPath.normalize
      }

      paths.foldLeft(paths.head) { case (common, it) =>
        common.iterator().asScala.zip(it.iterator().asScala)
            .takeWhile { case (c,p) => c==p}
            .map(_._1)
            .foldLeft(paths.head.getRoot) { case (base,child) => base.resolve(child)}
      }.toFile
    }
  }

  private case class SharedRoot(root: Root, projects: Seq[sbtStructure.ProjectData])

  private case class ProjectRoot(project: sbtStructure.ProjectData, root: Root) {
    def isInternal: Boolean = !isExternal

    def isExternal: Boolean = root.directory.isOutsideOf(project.base)
  }

  private case class Root(
    scope: Root.Scope,
    kind: Root.Kind,
    directory: File,
    managed: Boolean
  ) {
    lazy val basePathFromKnownHardcodedDefaultPaths: Option[File] = Root.DefaultPaths.collectFirst {
      //Example directory: /c/example-project/downstream/src/test/java (check if it parent ends with `src/test`)
      case paths if directory.parent.exists(_.endsWith(paths: _*)) => directory << (paths.length + 1)
    }
  }

  private object Root {
    private val DefaultPaths = Seq(
      Seq("src", "main"),
      Seq("src", "test"),
    )

    sealed trait Scope
    object Scope {
      case object Compile extends Scope
      case object Test extends Scope
    }

    sealed trait Kind
    object Kind {
      case object Sources extends Kind
      case object Resources extends Kind
    }
  }

  private class SharedSourceRootNameProvider {
    private var usedNames = Set.empty[String]
    private var counter = 1

    def nameFor(base: File): String = {
      val namedDirectory = if (base.getName == "shared") base.parent.getOrElse(base) else base
      val prefix = s"${namedDirectory.getName}-sources"

      val result = if (usedNames.contains(prefix)) {
        counter += 1
        s"$prefix-$counter"
      } else {
        prefix
      }

      usedNames += result
      result
    }
  }

  protected def prependModuleNameWithGroupName(moduleName: String, group: Option[String]): String =
    group
      .filterNot(_.isBlank)
      .map(groupName => if (groupName.endsWith(".")) groupName else s"$groupName.")
      .map(_ + moduleName)
      .getOrElse(moduleName)

  protected def createModuleNode(
    typeId: String,
    projectId: String,
    moduleName: String,
    moduleFileDirectoryPath: String,
    externalConfigPath: String,
    shouldCreateNestedModule: Boolean
  ): Node[_ <:ModuleData] = {
    if (shouldCreateNestedModule) {
      new NestedModuleNode(typeId, projectId, moduleName, moduleFileDirectoryPath, externalConfigPath)
    } else {
      new ModuleNode(typeId, projectId, moduleName, moduleFileDirectoryPath, externalConfigPath)
    }
  }

}
