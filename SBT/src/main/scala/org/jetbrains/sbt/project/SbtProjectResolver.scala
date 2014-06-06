package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskNotificationListener, ExternalSystemTaskNotificationEvent, ExternalSystemTaskId}
import com.intellij.openapi.externalSystem.model.project._
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.externalSystem.model.{ExternalSystemException, DataNode}
import com.intellij.openapi.roots.DependencyScope
import java.io.File
import module.SbtModuleType
import settings._
import structure._
import data._

/**
 * @author Pavel Fatin
 */
class SbtProjectResolver extends ExternalSystemProjectResolver[SbtExecutionSettings] {
  def resolveProjectInfo(id: ExternalSystemTaskId, projectPath: String, isPreview: Boolean, settings: SbtExecutionSettings, listener: ExternalSystemTaskNotificationListener): DataNode[ProjectData] = {
    val root = {
      val file = new File(projectPath)
      if (file.isDirectory) file.getPath else file.getParent
    }

    val runner = new SbtRunner(settings.ideaSystem, settings.vmOptions, settings.customLauncher, settings.customVm)

    val xml = runner.read(new File(root), !isPreview) { message =>
      listener.onStatusChange(new ExternalSystemTaskNotificationEvent(id, message.trim))
    } match {
      case Left(errors) => throw new ExternalSystemException(errors)
      case Right(node) => node
    }

    val data = StructureParser.parse(xml, new File(System.getProperty("user.home")))

    // TODO Show warnings about excluded roots when IDEA-123007 will be implemented
    // in the External System (UI interaction API for external system project resolver).
    //val externalSourceRoots = data.projects.map(externalSourceRootsIn)

    convert(root, data).toDataNode
  }

  private def convert(root: String, data: Structure): Node[ProjectData] = {
    val projects = data.projects

    val project = data.projects.headOption.getOrElse(throw new RuntimeException("No root project found"))

    val projectNode = new ProjectNode(project.name, root, root)

    val javaHome = project.java.flatMap(_.home).getOrElse(new File(System.getProperty("java.home")))
    val javacOptions = project.java.map(_.options).getOrElse(Seq.empty)

    projectNode.add(new ScalaProjectNode(javaHome, javacOptions))

    val libraries = {
      val repositoryModules = data.repository.map(_.modules).getOrElse(Seq.empty)

      val otherModuleIds = projects.flatMap(_.dependencies.modules.map(_.id)).toSet --
              repositoryModules.map(_.id).toSet

      repositoryModules.map(createResolvedLibrary) ++ otherModuleIds.map(createUnresolvedLibrary)
    }

    val compilerLibraries = {
      val scalas = projects.flatMap(_.scala).distinctBy(_.version)
      scalas.map(createCompilerLibrary)
    }

    projectNode.addAll(libraries ++ compilerLibraries)

    val moduleFilesDirectory = new File(root + "/" + Sbt.ModulesDirectory)

    val moduleNodes: Seq[ModuleNode] = projects.map { project =>
      val moduleNode = createModule(project, moduleFilesDirectory)
      moduleNode.add(createContentRoot(project))
      moduleNode.addAll(createLibraryDependencies(project.dependencies.modules)(moduleNode, libraries.map(_.data)))
      moduleNode.addAll(project.scala.map(createFacet(project, _)).toSeq)
      moduleNode.addAll(createUnmanagedDependencies(project.dependencies.jars)(moduleNode))
      moduleNode
    }

    projectNode.addAll(moduleNodes)

    projects.zip(moduleNodes).foreach { case (moduleProject, moduleNode) =>
      moduleProject.dependencies.projects.foreach { dependencyId =>
        val dependency = moduleNodes.find(_.getId == dependencyId.project).getOrElse(
          throw new ExternalSystemException("Cannot find project dependency: " + dependencyId.project))
        val data = new ModuleDependencyNode(moduleNode, dependency)
        data.setScope(scopeFor(dependencyId.configurations))
        data.setExported(true)
        moduleNode.add(data)
      }
    }

    projectNode.addAll(projects.map(createBuildModule(_, moduleFilesDirectory)))

    projectNode
  }

  private def createFacet(project: Project, scala: Scala): ScalaFacetNode = {
    val basePackage = Some(project.organization).filter(_.contains(".")).mkString

    new ScalaFacetNode(scala.version, basePackage, internalNameFor(scala), scala.options)
  }

  private def createUnresolvedLibrary(moduleId: ModuleId): LibraryNode = {
    val module = Module(moduleId, Seq.empty, Seq.empty, Seq.empty)
    createLibrary(module, resolved = false)
  }

  private def createResolvedLibrary(module: Module): LibraryNode = {
    createLibrary(module, resolved = true)
  }
  
  private def createLibrary(module: Module, resolved: Boolean): LibraryNode = {
    val result = new LibraryNode(nameFor(module.id), resolved)
    result.addPaths(LibraryPathType.BINARY, module.binaries.map(_.path))
    result.addPaths(LibraryPathType.DOC, module.docs.map(_.path))
    result.addPaths(LibraryPathType.SOURCE, module.sources.map(_.path))
    result
  }

  private def nameFor(id: ModuleId) = s"${id.organization}:${id.name}:${id.revision}"

  private def createCompilerLibrary(scala: Scala): LibraryNode = {
    val result = new LibraryNode(nameFor(scala), resolved = true)
    // TODO don't use custom delimiter either when the external system will preserve compiler libraries
    // or when we will adopt the new Scala project configuration scheme
    // (see processOrphanProjectLibraries in ExternalSystemUtil)
    result.setInternalName(internalNameFor(scala))
    val jars = scala.compilerJar +: scala.libraryJar +: scala.extraJars
    result.addPaths(LibraryPathType.BINARY, jars.map(_.path))
    result
  }

  private def nameFor(scala: Scala) = s"scala-compiler-bundle:${scala.version}"

  private def internalNameFor(scala: Scala) = "SBT:: " + nameFor(scala)

  private def createModule(project: Project, moduleFilesDirectory: File): ModuleNode = {
    // TODO use both ID and Name when related flaws in the External System will be fixed
    // TODO explicit canonical path is needed until IDEA-126011 is fixed
    val result = new ModuleNode(StdModuleTypes.JAVA.getId, project.id, project.id,
      moduleFilesDirectory.path, project.base.canonicalPath)

    result.setInheritProjectCompileOutputPath(false)

    project.configurations.find(_.id == "compile").foreach { configuration =>
      result.setCompileOutputPath(ExternalSystemSourceType.SOURCE, configuration.classes.path)
    }

    project.configurations.find(_.id == "test").foreach { configuration =>
      result.setCompileOutputPath(ExternalSystemSourceType.TEST, configuration.classes.path)
    }

    result
  }

  private def createContentRoot(project: Project): ContentRootNode = {
    val productionSources = validRootPathsIn(project, "compile")(_.sources)
    val productionResources = validRootPathsIn(project, "compile")(_.resources)
    val testSources = validRootPathsIn(project, "test")(_.sources) ++ validRootPathsIn(project, "it")(_.sources)
    val testResources = validRootPathsIn(project, "test")(_.resources) ++ validRootPathsIn(project, "it")(_.resources)

    val result = new ContentRootNode(project.base.path)

    result.storePaths(ExternalSystemSourceType.SOURCE, productionSources)
    result.storePaths(ExternalSystemSourceType.RESOURCE, productionResources)

    result.storePaths(ExternalSystemSourceType.TEST, testSources)
    result.storePaths(ExternalSystemSourceType.TEST_RESOURCE, testResources)

    if(canExcludeTargetIn(project)) {
      result.storePath(ExternalSystemSourceType.EXCLUDED, project.target.path)
    }

    result
  }

  // We cannot always exclude the whole ./target/ directory because of
  // the generated sources, so we resort to an heuristics.
  private def canExcludeTargetIn(project: Project): Boolean = {
    val managedDirectories = project.configurations
            .flatMap(configuration => configuration.sources ++ configuration.resources)
            .filter(_.managed)
            .map(_.file)

    val defaultNames = Set("main", "test")

    val relevantDirectories = managedDirectories.filter(file => file.exists || !defaultNames.contains(file.getName))

    relevantDirectories.forall(_.isOutsideOf(project.target))
  }

  private def createBuildModule(project: Project, moduleFilesDirectory: File): ModuleNode = {
    val id = project.id + Sbt.BuildModuleSuffix
    val name = project.name + Sbt.BuildModuleSuffix
    val buildRoot = project.base / Sbt.ProjectDirectory

    // TODO use both ID and Name when related flaws in the External System will be fixed
    // TODO explicit canonical path is needed until IDEA-126011 is fixed
    val result = new ModuleNode(SbtModuleType.instance.getId, id, id, moduleFilesDirectory.path, buildRoot.canonicalPath)

    result.setInheritProjectCompileOutputPath(false)
    result.setCompileOutputPath(ExternalSystemSourceType.SOURCE, (buildRoot / Sbt.TargetDirectory / "idea-classes").path)
    result.setCompileOutputPath(ExternalSystemSourceType.TEST, (buildRoot / Sbt.TargetDirectory / "idea-test-classes").path)

    result.add(createBuildContentRoot(buildRoot))

    val library = {
      val build = project.build
      val classes = build.classes.filter(_.exists).map(_.path)
      val docs = build.docs.filter(_.exists).map(_.path)
      val sources = build.sources.filter(_.exists).map(_.path)
      createModuleLevelDependency(Sbt.BuildLibraryName, classes, docs, sources, DependencyScope.COMPILE)(result)
    }

    result.add(library)

    result.add(createSbtModuleData(project))

    result
  }

  private def createBuildContentRoot(buildRoot: File): ContentRootNode = {
    val result = new ContentRootNode(buildRoot.path)

    val sourceDirs = Seq(buildRoot) // , base << 1

    val exludedDirs = Seq(
      buildRoot / Sbt.TargetDirectory,
      buildRoot / Sbt.ProjectDirectory / Sbt.TargetDirectory)

    result.storePaths(ExternalSystemSourceType.SOURCE, sourceDirs.map(_.path))
    result.storePaths(ExternalSystemSourceType.EXCLUDED, exludedDirs.map(_.path))

    result
  }

  def createSbtModuleData(project: Project): SbtModuleNode = {
    val imports = project.build.imports.flatMap(_.substring(7).split(", "))
    new SbtModuleNode(imports)
  }

  private def validRootPathsIn(project: Project, scope: String)
                              (selector: Configuration => Seq[Directory]): Seq[String] = {
    project.configurations
            .find(_.id == scope)
            .map(selector)
            .getOrElse(Seq.empty)
            .map(_.file)
            .filter(_.isUnder(project.base))
            .map(_.path)
  }

  private def externalSourceRootsIn(project: Project): Seq[File] = {
    val scopes = Set("compile", "test", "it")

    val sourceRoots = project.configurations
            .filter(it => scopes.contains(it.id))
            .flatMap(it => it.resources ++ it.resources)
            .map(_.file)

    sourceRoots.filter(_.isOutsideOf(project.base))
  }

  private def createLibraryDependencies(dependencies: Seq[ModuleDependency])(moduleData: ModuleData, libraries: Seq[LibraryData]): Seq[LibraryDependencyNode] = {
    dependencies.map { dependency =>
      val name = nameFor(dependency.id)
      val library = libraries.find(_.getExternalName == name).getOrElse(
        throw new ExternalSystemException("Library not found: " + name))
      val data = new LibraryDependencyNode(moduleData, library, LibraryLevel.PROJECT)
      data.setScope(scopeFor(dependency.configurations))
      data
    }
  }

  private def createUnmanagedDependencies(dependencies: Seq[JarDependency])(moduleData: ModuleData): Seq[LibraryDependencyNode] = {
    dependencies.groupBy(it => scopeFor(it.configurations)).toSeq.map { case (scope, dependency) =>
      val name = scope match {
        case DependencyScope.COMPILE => Sbt.UnmanagedLibraryName
        case it => s"${Sbt.UnmanagedLibraryName}-${it.getDisplayName.toLowerCase}"
      }
      val files = dependency.map(_.file.path)
      createModuleLevelDependency(name, files, Seq.empty, Seq.empty, scope)(moduleData)
    }
  }

  private def createModuleLevelDependency(name: String, classes: Seq[String], docs: Seq[String], sources: Seq[String], scope: DependencyScope)
                                         (moduleData: ModuleData): LibraryDependencyNode = {

    val libraryNode = new LibraryNode(name, resolved = true)
    libraryNode.addPaths(LibraryPathType.BINARY, classes)
    libraryNode.addPaths(LibraryPathType.DOC, docs)
    libraryNode.addPaths(LibraryPathType.SOURCE, sources)

    val result = new LibraryDependencyNode(moduleData, libraryNode, LibraryLevel.MODULE)
    result.setScope(scope)
    result
  }

  private def scopeFor(configurations: Seq[String]): DependencyScope = {
    val ids = configurations.toSet

    if (ids.contains("compile"))
      DependencyScope.COMPILE
    else if (ids.contains("test"))
      DependencyScope.TEST
    else if (ids.contains("runtime"))
      DependencyScope.RUNTIME
    else if (ids.contains("provided"))
      DependencyScope.PROVIDED
    else
      DependencyScope.COMPILE
  }

  def cancelTask(taskId: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener) = false
}
