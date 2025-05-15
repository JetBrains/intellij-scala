package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.model.project.{ExternalSystemSourceType, ModuleData}
import com.intellij.openapi.roots.DependencyScope
import org.jetbrains.plugins.scala.extensions.RichFile
import org.jetbrains.sbt.project.SbtProjectResolver.ImportContext
import org.jetbrains.sbt.project.SourceSetType.SourceSetType
import org.jetbrains.sbt.project.data._
import org.jetbrains.sbt.project.sources.SharedSourcesModuleType
import org.jetbrains.sbt.structure.{Dependencies, ProjectData, ProjectDependencyData}
import org.jetbrains.sbt.{structure => sbtStructure}

import java.io.File
import java.net.URI
import scala.reflect.ClassTag

/**
 * This trait contains utility methods responsible for handling for shared sources directories.
 * Such shared sources are typically "external" relative to the project base directory, hence the name "External"
 */
trait ExternalSourceRootResolution { self: SbtProjectResolver with ContentRootsResolution =>

  type ModuleDataNodeType = Node[_ <: ModuleData]

  protected sealed abstract class ModuleSourceSet(val parent: ModuleDataNodeType)
  protected case class PrentModuleSourceSet(override val parent: ModuleDataNodeType) extends ModuleSourceSet(parent)
  protected case class CompleteModuleSourceSet(override val parent: ModuleDataNodeType, main: SbtSourceSetModuleNode, test: SbtSourceSetModuleNode) extends ModuleSourceSet(parent)

  protected def addSharedSourceModules(
    groupedSharedRoots: Seq[SharedSourcesGroup],
    projectToSourceSet: Map[sbtStructure.ProjectData, ModuleSourceSet],
    libraryNodes: Seq[LibraryNode],
    defaultModuleFilesDirectory: String,
    separateProdTestSources: Boolean,
    buildProjectsGroups: Seq[BuildProjectsGroup]
  ): Unit = {
    val createSourceModule: (SharedSourcesGroup, Seq[LibraryNode], String, Seq[BuildProjectsGroup]) => ModuleDataNodeType =
      // note: we know that if separateProdTestSources are enabled, projectToSourceSet values will be of type CompleteModuleSourceSet
      // and if not, values will be of type PrentModuleSourceSet
      if (separateProdTestSources)
        createSharedSourcesModuleNode(_, castMapValues[CompleteModuleSourceSet](projectToSourceSet), _, _, _)
      else
        createSharedSourcesModuleNodeLegacy(_, castMapValues[PrentModuleSourceSet](projectToSourceSet), _, _, _)

    groupedSharedRoots.map(createSourceModule(_, libraryNodes, defaultModuleFilesDirectory, buildProjectsGroups))
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

      val scope = scopeFor(dependencyId.configurations.distinct)
      addModuleDependencyNode(moduleNode, dependency, scope, exported = false)
    }
  }

  private def castMapValues[R <: ModuleSourceSet : ClassTag](map: Map[sbtStructure.ProjectData, ModuleSourceSet]): Map[sbtStructure.ProjectData, R] =
    map.collect { case (key, value: R) => key -> value }

  /**
   * @see [[createSharedSourcesModuleNode]]
   */
  private def createSharedSourcesModuleNodeLegacy(
    rootGroup: SharedSourcesGroup,
    projectToModuleNode: Map[sbtStructure.ProjectData, PrentModuleSourceSet],
    libraryNodes: Seq[LibraryNode],
    defaultModuleFilesDirectory: String,
    buildProjectsGroups: Seq[BuildProjectsGroup]
  ): ModuleDataNodeType = {
    val projects = rootGroup.projects

    val sharedSourceModuleNode: ModuleDataNodeType = {
      val ownerProjectsIds = projects.map(projectToModuleNode).map(_.parent.getId)
      val representativeProject = representativeProjectIn(projects)
      val representativeProjectModule = projectToModuleNode.get(representativeProject)

      val moduleFilesDirectory = representativeProjectModule.map(_.parent.getModuleFileDirectoryPath).getOrElse(defaultModuleFilesDirectory)
      val (moduleNode, contentRootNode) = createSharedSourceModuleSimple(rootGroup, moduleFilesDirectory, ownerProjectsIds)
      //todo: get jdk from a corresponding jvm module ?
      moduleNode.add(ModuleSdkNode.inheritFromProject)

      moduleNode.add(createScalaSdkData(representativeProject.scala))

      val representativeProjectDependencies = representativeProject.dependencies

      // create unmanaged dependencies, we need to know how many of them there are, they need to be ordered before
      // the managed dependencies SCL-21852
      val unmanagedLibraryDependencies = representativeProjectDependencies.jars
      val unmanagedDependencies = createUnmanagedDependencies(unmanagedLibraryDependencies.forProduction)(moduleNode)

      //add library dependencies of the representative project
      val libraryDependencies = representativeProjectDependencies.modules
      moduleNode.addAll(createLibraryDependencies(libraryDependencies.forProduction)(moduleNode, libraryNodes.map(_.data), offset = unmanagedDependencies.size + 1, useSeparateProdTestSources = false))

      //add unmanaged jars/libraries dependencies of the representative project
      moduleNode.addAll(unmanagedDependencies)

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

      representativeProjectModule.foreach { case PrentModuleSourceSet(reprProjectModule) =>
        //put source module to the same module group
        extendModuleInternalNameWithGroupName(reprProjectModule, Some(moduleNode))
        //find rootNode for reprProjectModule, because shared sources module should be put in the same root
        val rootNode = findRootNodeForProjectData(representativeProject, buildProjectsGroups, projectToModuleNode)
        rootNode.foreach(_.add(moduleNode))
      }

      moduleNode
    }

    val dependentModulesThatRequireSharedSourcesModule = getModulesRequiringSharedModuleTransitivelyLegacy(projectToModuleNode, projects)

    //add the shared sources module as a dependency to platform modules
    val sharedSourceRootProjects = projects.map(projectToModuleNode).map { case PrentModuleSourceSet(module) =>
      (module, DependencyScope.COMPILE)
    }
    val allModulesThatRequireSharedSourcesModule = sharedSourceRootProjects ++ dependentModulesThatRequireSharedSourcesModule
    allModulesThatRequireSharedSourcesModule.foreach { case (ownerModule, dependencyScope) =>
      addModuleDependencyNode(ownerModule, sharedSourceModuleNode, dependencyScope)
    }

    sharedSourceModuleNode
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

  /**
   * @see [[createSharedSourcesModuleNodeLegacy]]
   */
  private def createSharedSourcesModuleNode(
    rootGroup: SharedSourcesGroup,
    projectToSourceSet: Map[sbtStructure.ProjectData, CompleteModuleSourceSet],
    libraryNodes: Seq[LibraryNode],
    defaultModuleFilesDirectory: String,
    buildProjectsGroups: Seq[BuildProjectsGroup]
  ): ModuleDataNodeType = {
    val projects = rootGroup.projects
    val (parentModule, sharedSourcesMainModule, sharedSourcesTestModule) = {
      val representativeProject = representativeProjectIn(projects)

      val (mainOwnerProjectsIds, testOwnerProjectsIds) = collectIdsOfSharedSourcesOwners(projects, projectToSourceSet)

      val sourceRootsWithType = rootGroup.sourceRoots.map(root => (root, calculateEsSourceType(root)))

      val allSourceModules = collectSourceModules(projectToSourceSet)
      val representativeProjectModule = projectToSourceSet.get(representativeProject)
      val moduleFilesDirectory = representativeProjectModule.map(_.parent.getModuleFileDirectoryPath).getOrElse(defaultModuleFilesDirectory)

      val sharedSourcesMainModule = createSharedSourceSetModule(
        rootGroup,
        moduleFilesDirectory,
        representativeProject,
        libraryNodes,
        SourceSetType.MAIN,
        mainOwnerProjectsIds,
        sourceRootsWithType,
        allSourceModules
      )
      val sharedSourcesTestModule = createSharedSourceSetModule(
        rootGroup,
        moduleFilesDirectory,
        representativeProject,
        libraryNodes,
        SourceSetType.TEST,
        testOwnerProjectsIds,
        sourceRootsWithType,
        allSourceModules
      )
      val parentModule = createParentSharedSourcesModule(rootGroup, moduleFilesDirectory)

      representativeProjectModule.foreach { case CompleteModuleSourceSet(reprProjectModule, _, _) =>
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
    addSourceSetModulesDependencies(parentModule, sharedSourcesMainModule, sharedSourcesTestModule)

    parentModule
  }

  private def addSourceSetModulesDependencies(parentModule: ModuleDataNodeType, sourceModules: Option[SbtSourceSetModuleNode]*): Unit =
    sourceModules.flatten.foreach { sourceModuleNode =>
      addModuleDependencyNode(parentModule, sourceModuleNode, DependencyScope.COMPILE, exported = false)
    }

  protected def collectSourceModules(projectToSourceSet: Map[sbtStructure.ProjectData, ModuleSourceSet]): Seq[ModuleDataNodeType] =
    projectToSourceSet.values.flatMap {
      case PrentModuleSourceSet(parent) => Seq(parent)
      case CompleteModuleSourceSet(_, main, test) => Seq(main, test)
    }.toSeq

  protected def addModuleDependencyNode(ownerModule: ModuleDataNodeType, module: ModuleDataNodeType, dependencyScope: DependencyScope, exported: Boolean = true): Unit = {
    val node = new ModuleDependencyNode(ownerModule, module)
    node.setScope(dependencyScope)
    module match {
      case sourceSetNode: SbtSourceSetModuleNode if sourceSetNode.sourceSetType == SourceSetType.TEST =>
        node.data.setProductionOnTestDependency(true)
      case _ =>
    }
    node.setExported(exported)
    ownerModule.add(node)
  }

  private def extendModuleInternalNameWithGroupName(
    reprProjectModule: ModuleDataNodeType,
    moduleNodes: Option[ModuleDataNodeType]*
  ): Unit = {
    val reprProjectModulePrefix = reprProjectModule.getInternalName.stripSuffix(reprProjectModule.getModuleName)
    // note: if reprProjectModulePrefix is blank, then it's a root module in the project,
    // and simply the internal module should be used instead
    val group =
      if (reprProjectModulePrefix.isBlank) reprProjectModule.getInternalName
      else reprProjectModulePrefix
    moduleNodes.collect { case Some(moduleNode) =>
      val moduleNameWithGroupName = prependModuleNameWithGroupName(moduleNode.getInternalName, Option(group))
      moduleNode.setInternalName(moduleNameWithGroupName)
    }
  }

  private def findRootNodeForProjectData(
    representativeProject: ProjectData,
    buildProjectsGroups: Seq[BuildProjectsGroup],
    projectToModuleNode: Map[sbtStructure.ProjectData, ModuleSourceSet]
  ): Option[ModuleDataNodeType] = {
    val rootProjectDataOpt = buildProjectsGroups
      .find(group => (group.projects :+ group.rootProject).contains(representativeProject))
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

  private def createSharedSourceModuleSimple(
    group: SharedSourcesGroup,
    moduleFilesDirectory: String,
    ownerProjectsIds: Seq[String]
  ): (ModuleDataNodeType, ContentRootNode) = {
    val groupBase = group.base
    val moduleNode = createModuleNode(
      SharedSourcesModuleType.instance.getId,
      group.name,
      group.name,
      moduleFilesDirectory,
      groupBase.canonicalPath,
      shouldCreateNestedModule = true
    )

    moduleNode.add(new SbtDisplayModuleNameNode(group.name))
    moduleNode.add(new SharedSourcesOwnersNode(SharedSourcesOwnersData(ownerProjectsIds)))

    val contentRootNode = new ContentRootNode(groupBase.path)
    group.sourceRoots.foreach { root =>
      val esSourceType = calculateEsSourceType(root)
      contentRootNode.storePath(esSourceType, root.directory.path)
    }

    moduleNode.add(contentRootNode)

    val contentRootData = contentRootNode.data
    val contentRootPath = contentRootData.getRootPath
    contentRootData.storePath(ExternalSystemSourceType.EXCLUDED, new File(contentRootPath, "target").getAbsolutePath)

    setupOutputDirectories(moduleNode, contentRootPath, sourceTypeFilter = None)

    (moduleNode, contentRootNode)
  }

  private def createParentSharedSourcesModule(group: SharedSourcesGroup, moduleFilesDirectory: String): ModuleDataNodeType = {
    val moduleNode = new NestedModuleNode(
      SharedSourcesModuleType.instance.getId,
      group.name,
      group.name,
      moduleFilesDirectory,
      group.base.canonicalPath
    )
    val contentRootNode = new ContentRootNode(group.base.path)
    moduleNode.add(new SbtDisplayModuleNameNode(group.name))
    contentRootNode.storePath(ExternalSystemSourceType.EXCLUDED, new File(group.base.path, "target").getAbsolutePath)
    moduleNode.add(contentRootNode)

    moduleNode.add(ModuleSdkNode.inheritFromProject)

    moduleNode
  }

  private def createSharedSourceSetModule(
    group: SharedSourcesGroup,
    moduleFilesDirectory: String,
    representativeProject: ProjectData,
    libraryNodes: Seq[LibraryNode],
    sourceSetName: SourceSetType,
    ownerProjectsIds: Seq[String],
    sourceRootsWithType: Seq[(SourceRoot, ExternalSystemSourceType)],
    allSourceModules: Seq[ModuleDataNodeType]
  ): Option[SbtSourceSetModuleNode] = {
    val groupPath = group.base.path

    val internalModuleName = s"${group.name}.$sourceSetName"
    val moduleNode = new SbtSourceSetModuleNode(
      SharedSourcesModuleType.instance.getId,
      internalModuleName,
      sourceSetName,
      moduleFilesDirectory,
      group.base.canonicalPath
    )
    moduleNode.setInternalName(internalModuleName)
    moduleNode.add(new SbtDisplayModuleNameNode(internalModuleName))
    moduleNode.add(new SharedSourcesOwnersNode(SharedSourcesOwnersData(ownerProjectsIds)))

    def isApplicableSource(sourceType: ExternalSystemSourceType): Boolean =
      if (sourceSetName == SourceSetType.TEST) sourceType.isTest
      else !sourceType.isTest

    // it is not needed to care about excluded because it is not possible to have excluded type see #calculateEsSourceType
    val sourceRoots = sourceRootsWithType
      .filter { case (_, sourceType) => isApplicableSource(sourceType) }
      .map { case (root, sourceType) => (root.directory.path, sourceType) }

    if (sourceRoots.nonEmpty) {
      // it is correct to hardcode a root path to src/main or src/test, because the current logic with shared sources
      // allows the creation of shared source only in those directories. See #basePathFromKnownHardcodedDefaultPaths
      val contentRootNodes = createContentRootNodes(
        sourceRootBaseDirs = Seq(s"$groupPath/src/$sourceSetName"),
        sourceRoots = sourceRoots,
      )
      moduleNode.addAll(contentRootNodes)
    } else {
      // when roots are empty, we shouldn't create a shared sources module
      return None
    }

    val esSourceType =
      if (sourceSetName == SourceSetType.TEST) ExternalSystemSourceType.TEST
      else ExternalSystemSourceType.SOURCE
    setupOutputDirectories(moduleNode, groupPath, Some(esSourceType))

    val scalaSdk = createScalaSdkData(representativeProject.scala)
    moduleNode.add(ModuleSdkNode.inheritFromProject)
    moduleNode.add(scalaSdk)

    val representativeProjectDependencies = representativeProject.dependencies

    def getScopedDependencies[T](deps: Dependencies[T]): Seq[T] =
      if (sourceSetName == SourceSetType.TEST) deps.forTest
      else deps.forProduction

    // create unmanaged dependencies, we need to know how many of them there are, they need to be ordered before
    // the managed dependencies SCL-21852
    val unmanagedLibraryDependencies = getScopedDependencies(representativeProjectDependencies.jars)
    val unmanagedDependencies = createUnmanagedDependencies(unmanagedLibraryDependencies)(moduleNode)

    //add library dependencies of the representative project
    val librariesNodeData = libraryNodes.map(_.data)
    val libraryDependencies = getScopedDependencies(representativeProjectDependencies.modules)
    moduleNode.addAll(createLibraryDependencies(libraryDependencies)(moduleNode, librariesNodeData, offset = unmanagedDependencies.size + 1, useSeparateProdTestSources = true))

    //add unmanaged jars/libraries dependencies of the representative project
    moduleNode.addAll(unmanagedDependencies)

    // add project dependencies of the representative project
    val moduleDependencies = getScopedDependencies(representativeProjectDependencies.projects)
    addModuleDependencies(moduleDependencies, allSourceModules, moduleNode)

    Some(moduleNode)
  }

  /**
   * NOTE 1: This is a workaround method<br>
   * NOTE 2: This workaround is not actual when separate main/test source modules are used
   *
   * The primary use case for this logic is to handle SBT projects with `projectmatrix` sbt plugin.<br>
   * You can inspect `sbt-projectmatrix-with-source-generators` test project as an example.
   *
   * Details:<br>
   * In sbt build with `projectmatrix` sbt plugin, for a single project multiple subprojects are generated.<br>
   * For example, if we define a single project {{{
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
    rootGroup: SharedSourcesGroup,
    representativeProject: ProjectData
  ): Set[SourceRoot] = {
    val rootGroupBase = rootGroup.base
    val representativeProjectBase = representativeProject.base

    val sourceRootsFromRepresentative: Seq[SourceRoot] = sourceRootsIn(representativeProject)
    sourceRootsFromRepresentative
      .filter(_.managed)
      .toSet
      //ensure that source roots are not already listed in root group roots to avoid duplicates
      .diff(rootGroup.sourceRoots.toSet)
      //ensure that source roots are in the content root of base module
      .filter(_.directory.isUnder(rootGroupBase))
      //get those source roots which are outside representative project content root
      .filterNot(_.directory.isUnder(representativeProjectBase))
  }

  /**
   * JPS compiler expects target directories:<br>
   * if they are missing, all sources are marked dirty, and there is no incremental compilation<br>
   * (SCL-16698)
   *
   * UPD: it seems like we don't have to actually create the directory and pollute the file system,
   * JPS just needs the output path to be registered
   *
   * @param sourceTypeFilter when set, only directories for that source type will be created
   * @see [[com.intellij.compiler.impl.CompileDriver.validateOutputs]]
   */
  private def setupOutputDirectories(
    moduleNode: ModuleDataNodeType,
    contentRoot: String,
    sourceTypeFilter: Option[ExternalSystemSourceType]
  ): Unit = {
    moduleNode.setInheritProjectCompileOutputPath(false)

    val dirs = Seq(
      (ExternalSystemSourceType.SOURCE, "target/classes"),
      (ExternalSystemSourceType.TEST, "target/test-classes")
    )
    val dirsFiltered = sourceTypeFilter match {
      case Some(st) => dirs.filter(_._1 == st)
      case None => dirs
    }
    dirsFiltered.foreach { case (sourceType, relPath) =>
      moduleNode.setCompileOutputPath(sourceType, new File(contentRoot, relPath).getAbsolutePath)
    }
  }

  private def calculateEsSourceType(root: SourceRoot): ExternalSystemSourceType =
    ExternalSystemSourceType.from(
      root.scope == SourceRoot.Scope.Test,
      root.managed,
      root.kind == SourceRoot.Kind.Resources,
      false
    )

  /**
   * @return list of shared source roots which meet these criteria:
   *         - the roots are located outside base paths of all sbt projects
   *         - the roots are used in more than one project (that's why it's called "shared")
   *
   * @note It's somehow similar to `org.jetbrains.bsp.project.importing.BspResolverLogic.sharedSourceEntries`
   *       (analog for the BSP external system)
   */
  private def sharedAndExternalRootsIn(projects: Seq[sbtStructure.ProjectData])
                                        (implicit context: ImportContext): Seq[SharedSourceRoot] = {
    val projectRootsExternal: Seq[ProjectSourceRoot] =
      getProjectSourceRootsExternalToAllProjects(projects)

    /**
     * ==When separate main/test sources mode is disabled==
     * We always treat external directory as shared because
     * we don't create source/content roots for such directories in IntelliJ modules corresponding to the project that
     * uses this root (see `SbtProjectResolver#validSourceRootPathsIn`)
     *
     * ==When separate main/test sources mode is enabled==
     * We treat the external root as "shared" in one of these cases:
     *  1. The directory is actually used in more than 1 projects
     *  1. The directory is a known/standard sbt location for shared sources (SCL-12520)
     *
     * Handle the default "shared" directory defined in `sbtcrossproject.CrossType.Full`.<br>
     * By default, in "Full" mode, the directory has this structure: {{{
     *   .
     *   ├── js
     *   ├── jvm
     *   ├── native
     *   └── shared
     *       └──src/main/scala
     *       └──src/main/scala2
     *       └──src/test/resources
     * }}}
     */
    def shouldTreatExternalDirectoryShared(sourceRoot: SourceRoot, projects: Set[ProjectData]): Boolean = {
      if (context.useSeparateProdTestSources) {
        // primarily for SCL-12520
        // TODO: also handle sbtcrossproject.CrossType.Pure.
        //  But for that we should ideally import the value of during structure extraction
        //  sbtcrossproject.CrossPlugin.autoImport$#crossProjectCrossType
        //  (only when sbt-crossproject sbt plugin is enable in the build)
        val FullCrossTypeSharedSourcesLocation = "shared"
        projects.size > 1 || sourceRoot.basePathGuessed.exists(_.name == FullCrossTypeSharedSourcesLocation)
      }
      else
        true
    }

    val sharedSourceRootsToProjects: Map[SourceRoot, Set[ProjectData]] =
      projectRootsExternal
        .groupBy(_.sourceRoot)
        .view.mapValues(_.map(_.project).toSet)
        .filter { case (sourceRoot, projects) =>
          shouldTreatExternalDirectoryShared(sourceRoot, projects)
        }
        .toMap

    sharedSourceRootsToProjects
      .map(p => SharedSourceRoot(p._1, p._2.toSeq))
      .toSeq
  }

  private def getProjectSourceRootsExternalToAllProjects(projects: Seq[sbtStructure.ProjectData]): Seq[ProjectSourceRoot] = {
    val projectSourceRoots: Seq[ProjectSourceRoot] =
      projects.flatMap(project => sourceRootsIn(project).map(ProjectSourceRoot(project,_)))

    val (projectRootsInternal, projectRootsExternal) =
      projectSourceRoots.partition(_.isInternal)

    // TODO return the message about omitted directories
    val sourceRootsInternal: Set[File] =
      projectRootsInternal.map(_.sourceRoot.directory).toSet

    projectRootsExternal.filter { externalProjectRoot =>
      !sourceRootsInternal.contains(externalProjectRoot.sourceRoot.directory)
    }
  }

  protected def groupSharedRoots(projects: Seq[ProjectData])(implicit context: ImportContext): Seq[SharedSourcesGroup] = {
    val sharedSourceRoots = sharedAndExternalRootsIn(projects)
    val nameProvider = new SharedSourceRootNameProvider()

    // TODO consider base/projects correspondence
    val rootsGroupedByBase = sharedSourceRoots.groupBy(_.sourceRoot.basePathGuessed)
    rootsGroupedByBase.toList.collect {
      //NOTE: ignore roots with empty base to avoid dangling "shared-sources" module
      case (Some(base), sharedRoots) =>
        val name = nameProvider.nameFor(base)
        val projects = sharedRoots.flatMap(_.projects).distinct
        SharedSourcesGroup(name, sharedRoots.map(_.sourceRoot), projects)
    }
  }

  private def sourceRootsIn(project: sbtStructure.ProjectData): Seq[SourceRoot] = {
    val relevantScopes = Set("compile", "test", "it")

    val relevantConfigurations = project.configurations.filter(it => relevantScopes.contains(it.id))

    relevantConfigurations.flatMap { configuration =>
      def createRoot(kind: SourceRoot.Kind)(directory: sbtStructure.DirectoryData): SourceRoot = {
        val scope = if (configuration.id == "compile") SourceRoot.Scope.Compile else SourceRoot.Scope.Test
        SourceRoot(scope, kind, directory.file.canonicalFile, directory.managed)
      }

      val sourceRoots = configuration.sources.map(createRoot(SourceRoot.Kind.Sources))
      val resourceRoots = configuration.resources.map(createRoot(SourceRoot.Kind.Resources))
      sourceRoots ++ resourceRoots
    }
  }

  /**
   * Represents a group of projects associated with a specific sbt build.
   * Note, a single sbt build can "consist of"/"refer to" multiple other builds using `ProjectRef`
   *
   * @param buildUri                    The URI representing the build this group is associated with.
   *                                    It can point to a directory or a GitHub repository
   * @param rootProject                 the root project of the build
   * @param projects                    a list of projects in the build
   * @param rootProjectModuleNameUnique a unique name for the root project's module
   */
  protected case class BuildProjectsGroup(
    buildUri: URI,
    rootProject: ProjectData,
    projects: Seq[ProjectData],
    rootProjectModuleNameUnique: String,
  )

  /**
   * Represents data required to create shared sources IntelliJ module
   *
   * @param name        shared sources module name
   * @param sourceRoots list of source roots which will be added to the shared sources module
   * @param projects    list of projects that should depend on the shared sources module
   */
  protected case class SharedSourcesGroup(
    name: String,
    sourceRoots: Seq[SourceRoot],
    projects: Seq[sbtStructure.ProjectData]
  ) {
    lazy val base: File = commonBase(sourceRoots)

    /**
     * Returns the common base directory for the roots.<br>
     * Example 1 {{{
     *   input:
     *     ./project1/shared/src/main/scala
     *     ./project1/shared/src/test/scala
     *     ./project1/shared/src/main/resources
     *     ./project1/shared/src/test/resources
     *   output:
     *     ./project1/shared/
     * }}}
     *
     * Example 2 {{{
     *   input:
     *     ./project1/shared/custom/dir/main/scala
     *     ./project1/shared/custom/dir/test/scala
     *     ./project1/shared/custom/dir/main/resources
     *     ./project1/shared/custom/dir/test/resources
     *   output:
     *     ./project1/shared/custom/dir
     * }}}
     */
    private def commonBase(roots: Seq[SourceRoot]): File = {
      import scala.jdk.CollectionConverters._
      val paths = roots.map { root =>
        root.basePathGuessed.getOrElse(root.directory)
          .getCanonicalFile.toPath.normalize
      }

      paths.foldLeft(paths.head) { case (common, it) =>
        common.iterator().asScala.zip(it.iterator().asScala)
          .takeWhile { case (c, p) => c == p }
          .map(_._1)
          .foldLeft(paths.head.getRoot) { case (base, child) => base.resolve(child) }
      }.toFile
    }
  }

  //TODO: move these private utility classes to to a companion object/utility object
  // these classes don't have to be inner and hold extra "outer" reference
  /**
   * Represents a source root used in multiple projects
   *
   * @param sourceRoot source root (e.g. ~ `myProject/js/src/main/java`
   * @param projects   list of projects in which the `sourceRoot` is used
   */
  protected case class SharedSourceRoot(sourceRoot: SourceRoot, projects: Seq[sbtStructure.ProjectData])

  /**
   * Represents a source root corresponding to some sbt project
   */
  private case class ProjectSourceRoot(project: sbtStructure.ProjectData, sourceRoot: SourceRoot) {
    /**
     * @return true if the source root is located inside the current project base directory<br>
     *         false otherwise (the source root can be in some external shared sources root)
     */
    def isInternal: Boolean = !sourceRoot.directory.isOutsideOf(project.base)
  }

  protected case class SourceRoot(
    scope: SourceRoot.Scope,
    kind: SourceRoot.Kind,
    directory: File,
    managed: Boolean
  ) {
    /**
     * In case the source root is located in some "well-known"/"standard" location like "src/main"
     * this field will contain the path to the parent of that location.<br>
     * Example: {{{
     *   directory : .../p1/shared/src/main/scala-2
     *   result    : Some(.../p1/shared/)
     *
     *   directory : .../p1/shared/custom/dir/scala-2
     *   result    : None
     * }}}
     */
    lazy val basePathGuessed: Option[File] = SourceRoot.DefaultPaths.collectFirst {
      //Example directory: /c/example-project/downstream/src/test/java (check if it parent ends with `src/test`)
      case paths if directory.parent.exists(_.endsWith(paths: _*)) => directory << (paths.length + 1)
    }
  }

  protected object SourceRoot {
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
