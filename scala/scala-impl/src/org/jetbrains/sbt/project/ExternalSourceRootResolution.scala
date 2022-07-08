package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.util.io.FileUtilRt
import org.jetbrains.sbt.project.data._
import org.jetbrains.sbt.project.sources.SharedSourcesModuleType
import org.jetbrains.sbt.structure.ProjectData
import org.jetbrains.sbt.{structure => sbtStructure}

import java.io.File

trait ExternalSourceRootResolution { self: SbtProjectResolver =>

  def createSharedSourceModules(projectToModuleNode: Map[sbtStructure.ProjectData, ModuleNode],
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

  def createSourceModuleNodesAndDependencies(rootGroup: RootGroup,
                                             projectToModuleNode: Map[sbtStructure.ProjectData, ModuleNode],
                                             libraryNodes: Seq[LibraryNode],
                                             moduleFilesDirectory: File): ModuleNode = {
    val projects = rootGroup.projects

    val sourceModuleNode = {
      val moduleNode = createSourceModule(rootGroup, moduleFilesDirectory)

      //todo: get jdk from a corresponding jvm module ?
      moduleNode.add(ModuleSdkNode.inheritFromProject)

      // Select a single project and clone its module / project dependencies.
      // It seems that dependencies of any single project should be enough to highlight files in the shared source module.
      // Please note that we mix source modules into other modules on compilation,
      // so source module dependencies are not relevant for compilation, only for highlighting.
      val representativeProject = representativeProjectIn(rootGroup.projects)

      //add library dependencies of the representative project
      val moduleDependencies = representativeProject.dependencies.modules
      moduleNode.addAll(createLibraryDependencies(moduleDependencies)(moduleNode, libraryNodes.map(_.data)))

      //add library dependencies of the representative project
      val projectDependencies = representativeProject.dependencies.projects
      projectDependencies.foreach { dependencyId =>
        val dependency =
          projectToModuleNode.values
            .find(_.getId == ModuleNode.combinedId(dependencyId.project, dependencyId.buildURI))
            .getOrElse(throw new ExternalSystemException("Cannot find project dependency: " + dependencyId.project))

        val dependencyNode = new ModuleDependencyNode(moduleNode, dependency)
        dependencyNode.setScope(scopeFor(dependencyId.configuration))
        moduleNode.add(dependencyNode)
      }

      projectToModuleNode.get(representativeProject).foreach { reprProjectModule =>
        //put source module to the same module group
        moduleNode.setIdeModuleGroup(reprProjectModule.getIdeModuleGroup)
      }

      moduleNode
    }

    //add shared sources module as a dependency to platform modules
    projects.map(projectToModuleNode).foreach { ownerModule =>
      val node = new ModuleDependencyNode(ownerModule, sourceModuleNode)
      node.setExported(true)
      ownerModule.add(node)
    }

    sourceModuleNode
  }

  // Selects an arbitrary project, preferable a JVM one.
  private def representativeProjectIn(projects: Seq[ProjectData]) = {
    val isNonJvmTitle = (title: String) =>
      title.endsWith("js") || title.endsWith("native")

    val isNonJvmProject = (project: ProjectData) =>
      isNonJvmTitle(project.id.toLowerCase) || isNonJvmTitle(project.name.toLowerCase)

    projects.partition(isNonJvmProject) match {
      case (nonJvmProjects, Seq()) => nonJvmProjects.head
      case (_, jvmProjects) => jvmProjects.head
    }
  }

  private def createSourceModule(group: RootGroup, moduleFilesDirectory: File): ModuleNode = {
    val moduleNode = new ModuleNode(SharedSourcesModuleType.instance.getId,
      group.name, group.name, moduleFilesDirectory.path, group.base.canonicalPath)

    val contentRootNode = {
      val node = new ContentRootNode(group.base.path)

      group.roots.foreach { root =>
        node.storePath(scopeAndKindToSourceType(root.scope, root.kind), root.directory.path)
      }

      node
    }

    moduleNode.add(contentRootNode)

    setupOutputDirectories(moduleNode, contentRootNode)

    moduleNode
  }

  //target directory are expected by jps compiler:
  //if they are missing all sources are marked dirty and there is no incremental compilation
  private def setupOutputDirectories(moduleNode: ModuleNode, contentRootNode: ContentRootNode): Unit = {
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

  private def scopeAndKindToSourceType(scope: Root.Scope, kind: Root.Kind): ExternalSystemSourceType =
    (scope, kind) match {
      case (Root.Scope.Compile, Root.Kind.Sources)    => ExternalSystemSourceType.SOURCE
      case (Root.Scope.Compile, Root.Kind.Resources)  => ExternalSystemSourceType.RESOURCE
      case (Root.Scope.Test, Root.Kind.Sources)       => ExternalSystemSourceType.TEST
      case (Root.Scope.Test, Root.Kind.Resources)     => ExternalSystemSourceType.TEST_RESOURCE
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
