package org.jetbrains.sbt
package project

import java.io.File

import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import org.jetbrains.sbt.project.data.{ContentRootNode, ModuleDependencyNode, LibraryNode, ModuleNode}
import org.jetbrains.sbt.project.sources.SharedSourcesModuleType
import org.jetbrains.sbt.project.structure.{Directory, Project}

/**
 * @author Pavel Fatin
 */
trait ExternalSourceRootResolution { self: SbtProjectResolver =>
  def createSharedSourceModules(projectToModuleNode: Map[Project, ModuleNode],
          libraryNodes: Seq[LibraryNode],
          moduleFilesDirectory: File): Seq[ModuleNode] = {

    val projects = projectToModuleNode.keys.toSeq

    externalSourceRootGroupsIn(projects).map { group =>
      createSourceModuleNodesAndDependencies(group, projectToModuleNode, libraryNodes, moduleFilesDirectory)
    }
  }

  def createSourceModuleNodesAndDependencies(rootGroup: RootGroup,
                                             projectToModuleNode: Map[Project, ModuleNode],
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
        dependencyNode.setScope(scopeFor(dependencyId.configurations))
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
        val sourceType = (root.scope, root.kind) match {
          case (Root.Scope.Compile, Root.Kind.Sources) => ExternalSystemSourceType.SOURCE
          case (Root.Scope.Compile, Root.Kind.Resources) => ExternalSystemSourceType.RESOURCE
          case (Root.Scope.Test, Root.Kind.Sources) => ExternalSystemSourceType.TEST
          case (Root.Scope.Test, Root.Kind.Resources) => ExternalSystemSourceType.TEST_RESOURCE
        }
        node.storePath(sourceType, root.directory.path)
      }

      node
    }

    moduleNode.add(contentRootNode)

    moduleNode
  }

  private def externalSourceRootGroupsIn(projects: Seq[Project]): Seq[RootGroup] = {
    val projectRoots = projects.flatMap(project => sourceRootsIn(project).map(ProjectRoot(project,_)))

    // TODO return the message about omitted directories
    val internalSourceDirectories = projectRoots.filter(_.isInternal).map(_.root.directory).toSeq

    val sharedRoots = projectRoots
            .filter(it => it.isExternal && !internalSourceDirectories.contains(it.root.directory))
            .groupBy(_.root)
            .mapValues(_.map(_.project).toSet)
            .map(p => SharedRoot(p._1, p._2.toSeq))
            .toSeq


    val nameProvider = new SharedSourceRootNameProvider()

    // TODO consider base/projects correspondence
    sharedRoots.groupBy(_.root.base).values.toSeq.map { roots =>
      val sharedRoot = roots.head
      val name = nameProvider.nameFor(sharedRoot.root.base)
      RootGroup(name, roots.map(_.root), sharedRoot.projects)
    }
  }

  private def sourceRootsIn(project: Project): Seq[Root] = {
    val relevantScopes = Set("compile", "test", "it")

    val relevantConfigurations = project.configurations.filter(it => relevantScopes.contains(it.id))

    relevantConfigurations.flatMap { configuration =>
      def createRoot(kind: Root.Kind)(directory: Directory) = {
        val scope = if (configuration.id == "compile") Root.Scope.Compile else Root.Scope.Test
        Root(scope, kind, directory.file.canonicalFile)
      }

      configuration.sources.map(createRoot(Root.Kind.Sources)) ++
              configuration.resources.map(createRoot(Root.Kind.Resources))
    }
  }

  private case class RootGroup(name: String, roots: Seq[Root], projects: Seq[Project]) {
    def base: File = {
      val root = roots.head
      root.base.getOrElse(root.directory)
    }
  }

  private case class SharedRoot(root: Root, projects: Seq[Project])

  private case class ProjectRoot(project: Project, root: Root) {
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

    def nameFor(base: Option[File]) = {
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
