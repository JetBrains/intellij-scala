package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.model.project.{ExternalSystemSourceType, ModuleData}
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.roots.DependencyScope
import org.jetbrains.plugins.scala.extensions.RichFile
import org.jetbrains.sbt.project.SourceSetType.SourceSetType
import org.jetbrains.sbt.project.data.{NestedModuleNode, _}
import org.jetbrains.sbt.project.sources.SharedSourcesModuleType
import org.jetbrains.sbt.structure.{Dependencies, ProjectData, ProjectDependencyData}
import org.jetbrains.sbt.{structure => sbtStructure}

import java.io.File
import java.net.URI
import scala.reflect.ClassTag

trait ExternalSourceRootResolution { self: SbtProjectResolver =>

  type ModuleDataNodeType = Node[_<:ModuleData]

  protected sealed abstract class ModuleSourceSet(val parent: ModuleDataNodeType)
  protected case class PrentModuleSourceSet(override val parent: ModuleDataNodeType) extends ModuleSourceSet(parent)
  protected case class CompleteModuleSourceSet(override val parent: ModuleDataNodeType, main: SbtSourceSetModuleNode, test: SbtSourceSetModuleNode) extends ModuleSourceSet(parent)

  protected def addSharedSourceModules(
    projectToSourceSet: Map[sbtStructure.ProjectData, ModuleSourceSet],
    libraryNodes: Seq[LibraryNode],
    moduleFilesDirectory: File,
    separateProdTestSources: Boolean,
    buildProjectsGroups: Seq[BuildProjectsGroup]
  ): Unit = {
    val projects = projectToSourceSet.keys.toSeq
    val sharedRoots = sharedAndExternalRootsIn(projects)
    val grouped = groupSharedRoots(sharedRoots)
    val createSourceModule =
      // note: we know that if separateProdTestSources are enabled, projectToSourceSet values will be of type CompleteModuleSourceSet
      // and if not, values will be of type PrentModuleSourceSet
      if (separateProdTestSources) {
        createSourceModuleNode(_, castMapValues[CompleteModuleSourceSet](projectToSourceSet), _, _, _)
      } else {
        createSourceModuleNodeLegacy(_, castMapValues[PrentModuleSourceSet](projectToSourceSet), _, _, _)
      }

    grouped.map(createSourceModule(_, libraryNodes, moduleFilesDirectory, buildProjectsGroups))
  }

  protected def addModuleDependencies(
    projectDependencies: Seq[ProjectDependencyData],
    allModules: Seq[ModuleDataNodeType],
    moduleNode: ModuleDataNodeType
  ): Unit = {
    projectDependencies.foreach { dependencyId =>
      val dependency = allModules
        .find(_.getId == ModuleNode.combinedId(dependencyId.project, dependencyId.buildURI))
        .getOrElse(throw new ExternalSystemException("Cannot find project dependency: " + dependencyId.project))

      val dependencyNode = new ModuleDependencyNode(moduleNode, dependency)
      dependencyNode.setScope(scopeFor(dependencyId.configurations.distinct))
      dependencyNode.setExported(false)
      moduleNode.add(dependencyNode)
    }
  }

  private def castMapValues[R <: ModuleSourceSet : ClassTag](map: Map[sbtStructure.ProjectData, ModuleSourceSet]): Map[sbtStructure.ProjectData, R] =
    map.collect { case (key, value: R) => key -> value }

  private def createSourceModuleNodeLegacy(
    rootGroup: RootGroup,
    projectToModuleNode: Map[sbtStructure.ProjectData, PrentModuleSourceSet],
    libraryNodes: Seq[LibraryNode],
    moduleFilesDirectory: File,
    buildProjectsGroups: Seq[BuildProjectsGroup]
  ): ModuleDataNodeType = {
    val projects = rootGroup.projects

    val sourceModuleNode = {
      val ownerProjectsIds = projects.map(projectToModuleNode).map(_.parent.getId)
      val (moduleNode, contentRootNode) = createSourceModule(rootGroup, moduleFilesDirectory, ownerProjectsIds)
      //todo: get jdk from a corresponding jvm module ?
      moduleNode.add(ModuleSdkNode.inheritFromProject)

      val representativeProject = representativeProjectIn(projects)
      moduleNode.add(createScalaSdkData(representativeProject.scala))

      val representativeProjectDependencies = representativeProject.dependencies

      //add library dependencies of the representative project
      val libraryDependencies = representativeProjectDependencies.modules
      moduleNode.addAll(createLibraryDependencies(libraryDependencies.forProduction)(moduleNode, libraryNodes.map(_.data)))

      //add unmanaged jars/libraries dependencies of the representative project
      val unmanagedLibraryDependencies = representativeProjectDependencies.jars
      moduleNode.addAll(createUnmanagedDependencies(unmanagedLibraryDependencies.forProduction)(moduleNode))

      //add project dependencies of the representative project
      val allSourceModules = projectToModuleNode.values.toSeq.map(_.parent)
      addModuleDependencies(representativeProjectDependencies.projects.forProduction, allSourceModules, moduleNode)

      //add some managed sources of the representative project
      //(see description of `getManagedSourceRootsFromRepresentativeProjectToIncludeAsBaseModelSourceRoots` method for the details)
      val representativeProjectManagedSources = getManagedSourceRootsFromRepresentativeProjectToIncludeAsBaseModelSourceRoots(rootGroup, representativeProject)
      representativeProjectManagedSources.foreach { root =>
        val esSourceType = calculateEsSourceType(root)
        contentRootNode.storePath(esSourceType, root.directory.path)
      }

      projectToModuleNode.get(representativeProject).foreach { case PrentModuleSourceSet(reprProjectModule) =>
        //put source module to the same module group
        extendModuleInternalNameWithGroupName(reprProjectModule, Some(moduleNode))
        //find rootNode for reprProjectModule, because shared sources module should be put in the same root
        val rootNode = findRootNodeForProjectData(representativeProject, buildProjectsGroups, projectToModuleNode)
        rootNode.foreach(_.add(moduleNode))
      }

      moduleNode
    }

    val dependentModulesThatRequireSharedSourcesModule = getModulesRequiringSharedModuleTransitivelyLegacy(projectToModuleNode, projects)


    //add shared sources module as a dependency to platform modules
    val sharedSourceRootProjects = projects.map(projectToModuleNode).map { case PrentModuleSourceSet(module) =>
      (module, DependencyScope.COMPILE)
    }
    val allModulesThatRequireSharedSourcesModule = sharedSourceRootProjects ++ dependentModulesThatRequireSharedSourcesModule
    allModulesThatRequireSharedSourcesModule.foreach { case (ownerModule, dependencyScope) =>
      addModuleDependencyNode(ownerModule, sourceModuleNode, dependencyScope)
    }

    sourceModuleNode
  }

  /**
   * Collects IDs of shared sources owners modules. It is needed to create [[org.jetbrains.sbt.project.SharedSourcesOwnersData]].
   * For more information see [[org.jetbrains.sbt.project.SharedSourcesOwnersData]] ScalaDoc.
   *
   * @return a tuple containing two sequences of strings.
   *         The first sequence in the tuple represents the IDs for the main shared sources module,
   *         while the second sequence represents the IDs for the test shared sources modules.
   */
  private def collectIdsOfSharedSourcesOwners(
    owners: Seq[ProjectData],
    projectToSourceSet: Map[sbtStructure.ProjectData, CompleteModuleSourceSet]
  ): (Seq[String], Seq[String]) = {
    val ownersModuleSourceSets = owners.map(projectToSourceSet)
    ownersModuleSourceSets.foldLeft((Seq.empty[String], Seq.empty[String])) {
      case ((mainIds, testIds), CompleteModuleSourceSet(_, main, test)) => (mainIds :+ main.id, testIds :+ test.id)
    }
  }

  private def createSourceModuleNode(
    rootGroup: RootGroup,
    projectToSourceSet: Map[sbtStructure.ProjectData, CompleteModuleSourceSet],
    libraryNodes: Seq[LibraryNode],
    moduleFilesDirectory: File,
    buildProjectsGroups: Seq[BuildProjectsGroup]
  ): ModuleDataNodeType = {
    val projects = rootGroup.projects
    val (parentModule, sharedSourcesMainModule, sharedSourcesTestModule) = {
      val representativeProject = representativeProjectIn(projects)

      val (mainOwnerProjectsIds, testOwnerProjectsIds) = collectIdsOfSharedSourcesOwners(projects, projectToSourceSet)

      // add some managed sources of the representative project
      // (see description of #getManagedSourceRootsFromRepresentativeProjectToIncludeAsBaseModelSourceRoots method for the details)
      val representativeProjectManagedSources = getManagedSourceRootsFromRepresentativeProjectToIncludeAsBaseModelSourceRoots(rootGroup, representativeProject).toSeq
      val rootsToSourceType = (rootGroup.roots ++ representativeProjectManagedSources).map(root => (root, calculateEsSourceType(root)))

      val allSourceModules = collectSourceModules(projectToSourceSet)
      val sharedSourcesMainModule = createSharedSourceSetModule(
        rootGroup,
        moduleFilesDirectory,
        representativeProject,
        libraryNodes,
        SourceSetType.MAIN,
        mainOwnerProjectsIds,
        rootsToSourceType,
        allSourceModules
      )
      val sharedSourcesTestModule = createSharedSourceSetModule(
        rootGroup,
        moduleFilesDirectory,
        representativeProject,
        libraryNodes,
        SourceSetType.TEST,
        testOwnerProjectsIds,
        rootsToSourceType,
        allSourceModules
      )
      val parentModule = createParentSharedSourcesModule(rootGroup, moduleFilesDirectory)

      projectToSourceSet.get(representativeProject).foreach { case CompleteModuleSourceSet(reprProjectModule, _, _) =>
        // put source module to the same module group
        extendModuleInternalNameWithGroupName(reprProjectModule, Some(parentModule), sharedSourcesMainModule, sharedSourcesTestModule)
        // find rootNode for reprProjectModule, because shared sources module should be put in the same root
        val rootNode = findRootNodeForProjectData(representativeProject, buildProjectsGroups, projectToSourceSet)
        rootNode.foreach(_.add(parentModule))
      }

      (parentModule, sharedSourcesMainModule, sharedSourcesTestModule)
    }

    val modulesRequiringSharedModules = getModulesRequiringSharedModulesTransitively(projectToSourceSet, projects)
    val modulesToSharedModuleWithScope = modulesRequiringSharedModules.map { case (module, dependency) =>
      val isTestProject = dependency.project.endsWith("test")
      val sharedSourcesModule = if (isTestProject) sharedSourcesTestModule else sharedSourcesMainModule
      (module, sharedSourcesModule, scopeFor(dependency.configurations))
    }

    // collect shared sources owner modules and shared sources modules to create dependencies
    val sharedSourcesOwnersToSharedModuleWithScope = projects.map(projectToSourceSet).flatMap { case CompleteModuleSourceSet(_, mainModule, testModule) =>
      Seq(
        (mainModule, sharedSourcesMainModule, DependencyScope.COMPILE), // shared sources main module in the platform main module
        (testModule, sharedSourcesMainModule, DependencyScope.COMPILE), // shared sources main module in the platform test module
        (testModule, sharedSourcesTestModule, DependencyScope.COMPILE) // shared sources test module in the platform test module
      )
    }

    val allModuleDependencies = modulesToSharedModuleWithScope ++ sharedSourcesOwnersToSharedModuleWithScope
    allModuleDependencies.collect { case (ownerModule, Some(sharedSourcesModule), scope) =>
      addModuleDependencyNode(ownerModule, sharedSourcesModule, scope)
    }

    Seq(sharedSourcesMainModule, sharedSourcesTestModule).collect { case Some(module) =>
      parentModule.add(module)
    }
    parentModule
  }

  protected def collectSourceModules(projectToSourceSet: Map[sbtStructure.ProjectData, ModuleSourceSet]): Seq[ModuleDataNodeType] =
    projectToSourceSet.values.flatMap {
      case PrentModuleSourceSet(parent) => Seq(parent)
      case CompleteModuleSourceSet(_, main, test) => Seq(main, test)
    }.toSeq

  private def addModuleDependencyNode(ownerModule: ModuleDataNodeType, module: ModuleDataNodeType, dependencyScope: DependencyScope): Unit = {
    val node = new ModuleDependencyNode(ownerModule, module)
    node.setScope(dependencyScope)
    node.setExported(true)
    ownerModule.add(node)
  }

  private def extendModuleInternalNameWithGroupName(
    reprProjectModule: ModuleDataNodeType,
    moduleNodes: Option[ModuleDataNodeType]*
  ): Unit = {
    val reprProjectModulePrefix = Option(reprProjectModule.getInternalName.stripSuffix(reprProjectModule.getModuleName))
    moduleNodes.collect { case Some(moduleNode) =>
      val moduleNameWithGroupName = prependModuleNameWithGroupName(moduleNode.getInternalName, reprProjectModulePrefix)
      moduleNode.setInternalName(moduleNameWithGroupName)
    }
  }

  private def findRootNodeForProjectData(
    representativeProject: ProjectData,
    buildProjectsGroups: Seq[BuildProjectsGroup],
    projectToModuleNode: Map[sbtStructure.ProjectData, ModuleSourceSet]
  ): Option[ModuleDataNodeType] = {
    val rootProjectDataOpt = buildProjectsGroups
      .find(_.projects.contains(representativeProject))
      .map(_.rootProject)
    rootProjectDataOpt.flatMap(projectToModuleNode.get).map(_.parent)
  }

  /**
   * If project transitive dependencies feature is on, it is required to put shared sources module not only in its owners' modules,
   * but in all modules that depend on the owners' modules.
   */
  private def getModulesRequiringSharedModuleTransitivelyLegacy(
    projectToModuleNode: Map[sbtStructure.ProjectData, PrentModuleSourceSet],
    sharedSourcesProjects: Seq[ProjectData]
  ): Seq[(ModuleDataNodeType, DependencyScope)] = {
    projectToModuleNode
      .filterNot { case (project, _) => sharedSourcesProjects.contains(project) }
      .flatMap { case (project, PrentModuleSourceSet(moduleNode)) =>
        val sharedSourcesDependencies = getProjectDependenciesOverlappingWithSharedProjects(project, sharedSourcesProjects)
        if (sharedSourcesDependencies.nonEmpty) {
          Some((moduleNode, scopeFor(sharedSourcesDependencies.flatMap(_.configurations))))
        } else None
      }.toSeq
  }

  private def getProjectDependenciesOverlappingWithSharedProjects(project: ProjectData, sharedSourcesProjects: Seq[ProjectData]): Seq[ProjectDependencyData] = {
    def isSharedSourcesDependency(dependency: ProjectDependencyData): Boolean =
      sharedSourcesProjects.exists { sharedSourcesProjectData =>
        val isTheSameSbtBuild = Option(sharedSourcesProjectData.buildURI) == dependency.buildURI
        isTheSameSbtBuild && sharedSourcesProjectData.id == dependency.project
      }

    val dependencies = project.dependencies.projects.forProduction
    dependencies.filter(isSharedSourcesDependency)
  }

  /**
   * If project transitive dependencies feature is on, it is required to put shared sources module not only in its owners' modules,
   * but in all modules that depend on the owners' modules.
   */
  private def getModulesRequiringSharedModulesTransitively(
    projectToModuleNode: Map[ProjectData, CompleteModuleSourceSet],
    sharedSourcesProjects: Seq[ProjectData]
  ): Seq[(SbtSourceSetModuleNode, ProjectDependencyData)] = {

    val sharedSourcesProjectIdMap = sharedSourcesProjects
      .groupBy(_.buildURI)
      .map { case (k, v) => Option(k) -> v }

    //note: it is a small hack, but ProjectDependencyData already has a suffix of the type, but ProjectData hasn't
    def dropSourceTypeSuffix(projectDependencyData: ProjectDependencyData) =
      projectDependencyData.project.dropRight(5)

    def filterOnlyRequiredDependencies(dependencies: Seq[ProjectDependencyData]): Seq[ProjectDependencyData] =
      dependencies
        .filter { projectDependencyData =>
          val sharedSourcesProjects = sharedSourcesProjectIdMap.getOrElse(projectDependencyData.buildURI, Seq.empty)
          val projectName = dropSourceTypeSuffix(projectDependencyData)
          sharedSourcesProjects.map(_.id).contains(projectName)
        }

    val moduleToDependencies = projectToModuleNode
      .filterNot { case (project, _) => sharedSourcesProjects.contains(project) }
      .flatMap { case (project, CompleteModuleSourceSet(_, main, test)) =>
        Seq((main, project.dependencies.projects.forProduction), (test, project.dependencies.projects.forTest))
      }

    moduleToDependencies
      .view.mapValues(filterOnlyRequiredDependencies).toSeq
      .flatMap { case (module, deps) => deps.map((module, _)) }
  }

  /**
   * Select a representative project (preferable a JVM one) among projects that share sources.
   * It's module / project dependencies will be copied to shared sources' module. It seems enough to highlight files in the shared source module.
   * Please note that we mix source modules into other modules on compilation,
   * so source module dependencies are not relevant for compilation, only for highlighting.
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
    ownerProjectsIds: Seq[String]
  ): (ModuleDataNodeType, ContentRootNode) = {
    val groupBase = group.base
    val moduleNode = createModuleNode(
      SharedSourcesModuleType.instance.getId,
      group.name,
      group.name,
      moduleFilesDirectory.path,
      groupBase.canonicalPath,
      shouldCreateNestedModule = true
    )

    moduleNode.add(new SharedSourcesOwnersNode(SharedSourcesOwnersData(ownerProjectsIds)))

    val contentRootNode = new ContentRootNode(groupBase.path)
    group.roots.foreach { root =>
      val esSourceType = calculateEsSourceType(root)
      contentRootNode.storePath(esSourceType, root.directory.path)
    }

    moduleNode.add(contentRootNode)

    setupOutputDirectories(moduleNode, contentRootNode)

    (moduleNode, contentRootNode)
  }

  private def createParentSharedSourcesModule(group: RootGroup, moduleFilesDirectory: File): ModuleDataNodeType = {
    val moduleNode = new NestedModuleNode(
      SharedSourcesModuleType.instance.getId,
      group.name,
      group.name,
      moduleFilesDirectory.path,
      group.base.canonicalPath
    )
    val contentRootNode = new ContentRootNode(group.base.path)
    contentRootNode.storePath(ExternalSystemSourceType.EXCLUDED, getOrCreateTargetDir(group.base.path, "target").getAbsolutePath)
    moduleNode.add(contentRootNode)

    moduleNode.add(ModuleSdkNode.inheritFromProject)

    moduleNode
  }

  private def createSharedSourceSetModule(
    group: RootGroup,
    moduleFilesDirectory: File,
    representativeProject: ProjectData,
    libraryNodes: Seq[LibraryNode],
    sourceSetName: SourceSetType,
    ownerProjectsIds: Seq[String],
    rootsToSourceType: Seq[(Root, ExternalSystemSourceType)],
    allSourceModules: Seq[ModuleDataNodeType]
  ): Option[SbtSourceSetModuleNode] = {
    val groupPath = group.base.path

    val internalModuleName = s"${group.name}.$sourceSetName"
    val moduleNode = new SbtSourceSetModuleNode(
      SharedSourcesModuleType.instance.getId,
      internalModuleName,
      sourceSetName.toString,
      moduleFilesDirectory.path,
      group.base.canonicalPath
    )
    moduleNode.setInternalName(internalModuleName)

    moduleNode.add(new SharedSourcesOwnersNode(SharedSourcesOwnersData(ownerProjectsIds)))

    def isApplicableSource(sourceType: ExternalSystemSourceType): Boolean =
      if (sourceSetName == SourceSetType.TEST) sourceType.isTest
      else !sourceType.isTest

    // it is not needed to care about excluded because it is not possible to have excluded type see #calculateEsSourceType
    val roots = rootsToSourceType
      .filter { case (_, sourceType) => isApplicableSource(sourceType) }
      .map { case (root, sourceType) => (root.directory.path, sourceType) }

    if (roots.nonEmpty) {
      // it is correct to hardcode a root path to src/main or src/test, because the current logic with shared sources
      // allows the creation of shared source only in those directories. See #basePathFromKnownHardcodedDefaultPaths
      val contentRootNodes = createContentRootNodes(roots, Seq(s"$groupPath/src/$sourceSetName"))
      moduleNode.addAll(contentRootNodes)
    } else {
      // when roots are empty, we shouldn't create a shared sources module
      return None
    }

    moduleNode.setInheritProjectCompileOutputPath(false)

    val (relPath, externalSystemSourceType) =
      if (sourceSetName == SourceSetType.TEST) ("target/test-classes", ExternalSystemSourceType.TEST)
      else ("target/classes", ExternalSystemSourceType.SOURCE)

    setupOutputDirectoryBasedOnRelPath(moduleNode, groupPath, externalSystemSourceType, relPath)

    val scalaSdk = createScalaSdkData(representativeProject.scala)
    moduleNode.add(ModuleSdkNode.inheritFromProject)
    moduleNode.add(scalaSdk)

    val representativeProjectDependencies = representativeProject.dependencies

    def getScopedDependencies[T](deps: Dependencies[T]): Seq[T] =
      if (sourceSetName == SourceSetType.TEST) deps.forTest
      else deps.forProduction

    //add library dependencies of the representative project
    val librariesNodeData = libraryNodes.map(_.data)
    val libraryDependencies = getScopedDependencies(representativeProjectDependencies.modules)
    moduleNode.addAll(createLibraryDependencies(libraryDependencies)(moduleNode, librariesNodeData))

    //add unmanaged jars/libraries dependencies of the representative project
    val unmanagedLibraryDependencies = getScopedDependencies(representativeProjectDependencies.jars)
    moduleNode.addAll(createUnmanagedDependencies(unmanagedLibraryDependencies)(moduleNode))

    // add project dependencies of the representative project
    val moduleDependencies = getScopedDependencies(representativeProjectDependencies.projects)
    addModuleDependencies(moduleDependencies, allSourceModules, moduleNode)

    Some(moduleNode)
  }

  protected def createContentRootNodes(
    roots: Seq[(String, ExternalSystemSourceType)],
    rootPaths: Seq[String]
  ): Seq[ContentRootNode] = {
    val contentRootNodes = rootPaths.distinct.map(path => new ContentRootNode(path))
    roots.foldLeft(contentRootNodes) { case (nodes, (root, sourceType)) =>
      val rootFile = new File(root)
      val suitableContentRootNode = nodes.find { node =>
        val dataRootFile = new File(node.data.getRootPath)
        rootFile.isUnder(dataRootFile, strict = false)
      }
      suitableContentRootNode match {
        case Some(contentRootNode) =>
          contentRootNode.storePath(sourceType, root)
          nodes
        case None =>
          val node = new ContentRootNode(root)
          node.storePath(sourceType, root)
          nodes :+ node
      }
    }
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
  private def setupOutputDirectories(moduleNode: ModuleDataNodeType, contentRootNode: ContentRootNode): Unit = {
    val contentRootData = contentRootNode.data
    val contentRoot = contentRootData.getRootPath
    contentRootData.storePath(ExternalSystemSourceType.EXCLUDED, getOrCreateTargetDir(contentRoot, "target").getAbsolutePath)

    moduleNode.setInheritProjectCompileOutputPath(false)

    Seq((ExternalSystemSourceType.SOURCE, "target/classes"), (ExternalSystemSourceType.TEST, "target/test-classes")).foreach { case (sourceType, relPath) =>
        setupOutputDirectoryBasedOnRelPath(moduleNode, contentRoot, sourceType, relPath)
    }
  }

  private def setupOutputDirectoryBasedOnRelPath(
    moduleNode: ModuleDataNodeType,
    basePath: String,
    sourceType: ExternalSystemSourceType,
    relPath: String
  ): Unit =
    moduleNode.setCompileOutputPath(sourceType, getOrCreateTargetDir(basePath, relPath).getAbsolutePath)

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

  protected def prependModuleNameWithGroupName(moduleName: String, group: Option[String]): String = {
    val moduleNameWithGroupPrefix = group
      .filterNot(_.isBlank)
      // the group name might ended with a dot, when it is from org/jetbrains/sbt/project/ExternalSourceRootResolution.scala:111
      // and can be without a dot, when it is from org.jetbrains.sbt.project.SbtProjectResolver#createModuleWithAllRequiredData
      .map(groupName => if (groupName.endsWith(".")) groupName else s"$groupName.")
      .map(_ + moduleName)

    moduleNameWithGroupPrefix.getOrElse(moduleName)
  }

  protected def createModuleNode(
    typeId: String,
    projectId: String,
    moduleName: String,
    moduleFileDirectoryPath: String,
    externalConfigPath: String,
    shouldCreateNestedModule: Boolean
  ): ModuleDataNodeType = {
    if (shouldCreateNestedModule) {
      new NestedModuleNode(typeId, projectId, moduleName, moduleFileDirectoryPath, externalConfigPath)
    } else {
      new ModuleNode(typeId, projectId, moduleName, moduleFileDirectoryPath, externalConfigPath)
    }
  }
}

object SourceSetType extends Enumeration {
  type SourceSetType = Value
  final val MAIN = Value("main")
  final val TEST = Value("test")
}
