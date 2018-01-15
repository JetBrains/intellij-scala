package org.jetbrains.sbt
package project

import java.io.File

import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import org.jetbrains.sbt.project.data.{ContentRootNode, LibraryNode, ModuleDependencyNode, ModuleNode}
import org.jetbrains.sbt.project.sources.SharedSourcesModuleType
import org.jetbrains.sbt.{structure => sbtStructure}

/**
 * @author Pavel Fatin
 */
trait ExternalSourceRootResolution { self: SbtProjectResolver =>

  def createSharedSourceModules(projectToModuleNode: Map[sbtStructure.ProjectData, ModuleNode],
                                libraryNodes: Seq[LibraryNode],
                                moduleFilesDirectory: File,
                                warnings: String => Unit
                               ): Seq[ModuleNode] = {

    val projects = projectToModuleNode.keys.toSeq

    val (sharedRoots, externalRoots) = sharedAndExternalRootsIn(projects).partition(_.projects.length > 1)

    if (externalRoots.nonEmpty) {
      val externalRootsStr = externalRoots.map(_.root.directory).distinct.mkString("<ul><li>", "</li><li>", "</li></ul>")
      val msg =
        s"""
          | <p>
          | The following source roots are outside of the corresponding base directories:
          | $externalRootsStr
          | These source roots cannot be included in the IDEA project model.
          | </p><p>
          | <strong>Solution:</strong> declare an sbt project for these sources and include the project in dependencies.
          | </p>
        """.stripMargin

      warnings(msg)
    }

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

      val uniqueModuleDependencies = projects.flatMap(_.dependencies.modules).distinct
      moduleNode.addAll(createLibraryDependencies(uniqueModuleDependencies)(moduleNode, libraryNodes.map(_.data)))

      val uniqueProjectDependencies = projects.flatMap(_.dependencies.projects).distinct
      uniqueProjectDependencies.foreach { dependencyId =>
        val dependency = projectToModuleNode.values.find(_.getId == dependencyId.project).getOrElse(
          throw new ExternalSystemException("Cannot find project dependency: " + dependencyId.project))

        val dependencyNode = new ModuleDependencyNode(moduleNode, dependency)
        dependencyNode.setScope(scopeFor(dependencyId.configuration))
        moduleNode.add(dependencyNode)
      }

      moduleNode
    }

    projects.map(projectToModuleNode).foreach { ownerModule =>
      val node = new ModuleDependencyNode(ownerModule, sourceModuleNode)
      node.setExported(true)
      ownerModule.add(node)
    }

    sourceModuleNode
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

    moduleNode
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
      .mapValues(_.map(_.project).toSet)
      .map(p => SharedRoot(p._1, p._2.toSeq))
      .toSeq
  }

  private def groupSharedRoots(roots: Seq[SharedRoot]): Seq[RootGroup] = {
    val nameProvider = new SharedSourceRootNameProvider()

    // TODO consider base/projects correspondence
    roots.groupBy(_.root.base).values.toSeq.map { roots =>
      val sharedRoot = roots.head
      val name = nameProvider.nameFor(sharedRoot.root.base)
      RootGroup(name, roots.map(_.root), sharedRoot.projects)
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
      import scala.collection.JavaConverters._
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
    var usedNames = Set.empty[String]
    var counter = 1

    def nameFor(base: Option[File]): String = {
      val namedDirectory = if (base.exists(_.getName == "shared")) base.flatMap(_.parent) else base
      val prefix = namedDirectory.map(_.getName + "-sources").getOrElse("shared-sources")
      if (usedNames.contains(prefix)) {
        val result = prefix + counter
        counter += 1
        usedNames += result
        result
      } else {
        prefix
      }
    }
  }
}
