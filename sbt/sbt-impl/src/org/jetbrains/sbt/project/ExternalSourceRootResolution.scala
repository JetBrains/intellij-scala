package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.model.project.{ExternalSystemSourceType, ModuleData}
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.util.io.FileUtilRt
import org.jetbrains.plugins.scala.extensions.RichFile
import org.jetbrains.sbt.project.data._
import org.jetbrains.sbt.project.module.SourceModule
import org.jetbrains.sbt.project.sources.SharedSourcesModuleType
import org.jetbrains.sbt.structure.{DependencyData, ProjectData}
import org.jetbrains.sbt.{structure => sbtStructure}

import java.io.File
import scala.annotation.nowarn
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.Try


trait ExternalSourceRootResolution { self: SbtProjectResolver =>

  def createSharedSourceModules(projectToModuleNode: Map[sbtStructure.ProjectData, ProjectModules],
                                libraryNodes: Seq[LibraryNode],
                                moduleFilesDirectory: File
                               ): Seq[ModuleNode] = {

    val projects = projectToModuleNode.keys.toSeq
    val sharedRoots = sharedAndExternalRootsIn(projects)
    val grouped = groupSharedRoots(sharedRoots)
    grouped.map { group =>
      createSourceModuleNodesAndDependencies(group, projectToModuleNode, libraryNodes, moduleFilesDirectory)
    }
  }

  private def createSourceModuleNodesAndDependencies(
    rootGroup: RootGroup,
    projectToModuleNode: Map[sbtStructure.ProjectData, ProjectModules],
    libraryNodes: Seq[LibraryNode],
    moduleFilesDirectory: File
  ): ModuleNode = {
    val projects = rootGroup.projects

    val sourceModuleNode = {
      // Select a single project and clone its module / project dependencies.
      // It seems that dependencies of any single project should be enough to highlight files in the shared source module.
      // Please note that we mix source modules into other modules on compilation,
      // so source module dependencies are not relevant for compilation, only for highlighting.
      val representativeProject = representativeProjectIn(rootGroup.projects)
      val representativeProjectDependencies = representativeProject.dependencies

      val allModules = projectToModuleNode.values.toSeq.flatMap(_.productIterator.toSeq.map(_.asInstanceOf[ModuleNode]))
      val moduleNode = createModule(representativeProjectDependencies, libraryNodes, rootGroup, moduleFilesDirectory, allModules)

      //todo: get jdk from a corresponding jvm module ?
      moduleNode.parentModule.add(ModuleSdkNode.inheritFromProject)

      projectToModuleNode.get(representativeProject).foreach { reprProjectModule =>
        //put source module to the same module group
        // TODO
        moduleNode.parentModule.setIdeModuleGroup(reprProjectModule.parentModule.getIdeModuleGroup): @nowarn("cat=deprecation") // TODO: SCL-21288
      }
      moduleNode
    }

    //add shared sources module as a dependency to platform modules
    projects.map(projectToModuleNode).foreach { ownerModule =>
      def addModuleDependency(ownerModule: ModuleNode, sourceModuleNode: ModuleNode): Unit = {
        val node = new ModuleDependencyNode(ownerModule, sourceModuleNode)
        node.setExported(true)
        ownerModule.add(node)
      }
      addModuleDependency(ownerModule.productionModule, sourceModuleNode.productionModule)
      addModuleDependency(ownerModule.testModule, sourceModuleNode.testModule)
    }

    sourceModuleNode.parentModule
  }

  /**
   * Selects an arbitrary project, preferable a JVM one
   *
   * Also see [[org.jetbrains.plugins.scala.project.ModuleExt.findRepresentativeModuleForSharedSourceModule]]
   */
  private def representativeProjectIn(projects: Seq[ProjectData]): ProjectData = {
    val isNonJvmTitle = (title: String) =>
      title.endsWith("js") || title.endsWith("native")

    val isNonJvmProject = (project: ProjectData) =>
      isNonJvmTitle(project.id.toLowerCase) || isNonJvmTitle(project.name.toLowerCase)

    projects.partition(isNonJvmProject) match {
      case (nonJvmProjects, Seq()) => nonJvmProjects.head
      case (_, jvmProjects) => jvmProjects.head
    }
  }

  private def createModule(dependencyData: DependencyData, libraryNodes: Seq[LibraryNode],
                                 group: RootGroup, moduleFilesDirectory: File, allModules: Seq[ModuleNode]): ProjectModules = {
    // parent module
    val parentModule = new ModuleNode(SharedSourcesModuleType.instance.getId,
      group.name, group.name, moduleFilesDirectory.path, group.base.canonicalPath)

    // test module
    val testModule = createSourceModule(group.name, group.base.canonicalPath, moduleFilesDirectory.path, SourceModule.Test.kind)
    testModule.addAll(createLibraryDependencies(dependencyData.modules.forTestSources)(testModule, libraryNodes.map(_.data)))
    testModule.addAll(createUnmanagedDependencies(dependencyData.jars.forTestSources)(testModule))

    // production module
    val productionModule = createSourceModule(group.name, group.base.canonicalPath, moduleFilesDirectory.path, SourceModule.Production.kind)
    productionModule.addAll(createLibraryDependencies(dependencyData.modules.forProductionSources)(productionModule, libraryNodes.map(_.data)))
    productionModule.addAll(createUnmanagedDependencies(dependencyData.jars.forProductionSources)(productionModule))

    // add project dependencies
    createSourceModulesDependencies(dependencyData.projects, productionModule, testModule, allModules)

    // content root nodes
    val forbiddenPaths = group.projects.flatMap { project => Seq(project.sourceDirectory.canonicalPath, project.base.canonicalPath) }
    val groupedRoots = group.roots.groupBy(_.scope)
    def getContentRootNodes(rootsOpt: Option[Seq[Root]], typeForSource: ExternalSystemSourceType, typeForResource: ExternalSystemSourceType, alreadyCreated: Seq[String]) =
      rootsOpt.map { roots =>
        val grouped = roots.map {
          case Root(_, Root.Kind.Sources, dir) => (dir.path, typeForSource)
          case Root(_, Root.Kind.Resources, dir) => (dir.path, typeForResource)
        }
        createContentRootNodes(grouped, forbiddenPaths ++ alreadyCreated)
      }.getOrElse(Nil)

    val productionContentRootsNodes = getContentRootNodes(groupedRoots.get(Root.Scope.Compile), ExternalSystemSourceType.SOURCE, ExternalSystemSourceType.RESOURCE, Nil)
    productionModule.addAll(productionContentRootsNodes)
    val testContentRootsNodes = getContentRootNodes(groupedRoots.get(Root.Scope.Test), ExternalSystemSourceType.TEST, ExternalSystemSourceType.TEST_RESOURCE, productionContentRootsNodes.map(_.data.getRootPath))
    testModule.addAll(testContentRootsNodes)

    val parentContentRoot = new ContentRootNode(group.base.path)
    setupOutputDirectories(parentModule, parentContentRoot, productionModule, testModule)
    parentModule.addAll(parentContentRoot, productionModule, testModule)

    ProjectModules(parentModule, productionModule, testModule)
  }

  private def createSourceModule(groupName: String, groupBase: String, moduleFilesDirectory: String, sourceModuleKind: String): ModuleNode = {
    val id = ModuleNode.combinedId(s"$groupName:$sourceModuleKind", None)
    val moduleNode = new ModuleNode(SharedSourcesModuleType.instance.getId, id, sourceModuleKind, moduleFilesDirectory, groupBase)
    moduleNode.setInternalName(s"$groupName.$sourceModuleKind")
    moduleNode.data.setModuleName(sourceModuleKind)
    moduleNode.add(new SbtModuleNode(SbtModuleData(s"$groupName:$sourceModuleKind", None, isSourceModule = true)))
    moduleNode.add(ModuleSdkNode.inheritFromProject)
    moduleNode
  }

  //target directory are expected by jps compiler:
  //if they are missing all sources are marked dirty and there is no incremental compilation
  private def setupOutputDirectories(parentModuleNode: ModuleNode, parentContentRootNode: ContentRootNode, productionModuleNode: ModuleNode, testModuleNode: ModuleNode): Unit = {
    parentModuleNode.setInheritProjectCompileOutputPath(false)
    productionModuleNode.setInheritProjectCompileOutputPath(false)
    testModuleNode.setInheritProjectCompileOutputPath(false)

    val rootPath = ExternalSystemApiUtil.toCanonicalPath(parentContentRootNode.data.getRootPath)

    parentContentRootNode.storePath(ExternalSystemSourceType.EXCLUDED, getOrCreateTargetDir(rootPath, "target").getAbsolutePath)
    productionModuleNode.setCompileOutputPath(ExternalSystemSourceType.SOURCE, getOrCreateTargetDir(rootPath, "target/classes").getAbsolutePath)
    testModuleNode.setCompileOutputPath(ExternalSystemSourceType.TEST, getOrCreateTargetDir(rootPath, "target/test-classes").getAbsolutePath)
  }

  private def getOrCreateTargetDir(root: String, relPath: String): File = {
    val file = new File(root, relPath)
    if (!file.exists()) {
      FileUtilRt.createDirectory(file)
    }
    file
  }

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
    roots.groupBy(_.root.base).toList.map {
      case (base, sharedRoots) =>
        val name = nameProvider.nameFor(base)
        val projects = sharedRoots.flatMap(_.projects).distinct
        RootGroup(name, sharedRoots.map(_.root), projects)
    }
  }

  private def sourceRootsIn(project: sbtStructure.ProjectData): Seq[Root] = {
    val relevantScopes = Set("compile", "test", "it")

    val relevantConfigurations = project.configurations.filter(it => relevantScopes.contains(it.id))

    relevantConfigurations.flatMap { configuration =>
      def createRoot(kind: Root.Kind)(directory: sbtStructure.DirectoryData) = {
        val scope = if (configuration.id == "compile") Root.Scope.Compile else Root.Scope.Test
        Root(scope, kind, directory.file.canonicalFile)
      }

      configuration.sources.map(createRoot(Root.Kind.Sources)) ++
              configuration.resources.map(createRoot(Root.Kind.Resources))
    }
  }

  private case class RootGroup(name: String, roots: Seq[Root], projects: Seq[sbtStructure.ProjectData]) {
    def base: File = commonBase(roots)

    private def commonBase(roots: Seq[Root]) = {
      import scala.jdk.CollectionConverters._
      val paths = roots.map { root =>
        root.base.getOrElse(root.directory)
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

  private case class Root(scope: Root.Scope, kind: Root.Kind, directory: File) {
    def base: Option[File] = Root.DefaultPaths.collectFirst {
      case paths if directory.parent.exists(_.endsWith(paths: _*)) => directory << (paths.length + 1)
    }
  }

  private object Root {
    private val DefaultPaths = Seq(Seq("src", "main"), Seq("src", "test"))

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

    def nameFor(base: Option[File]): String = {
      val namedDirectory = if (base.exists(_.getName == "shared")) base.flatMap(_.parent) else base
      val prefix = namedDirectory.map(_.getName + "-sources").getOrElse("shared-sources")

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
}
