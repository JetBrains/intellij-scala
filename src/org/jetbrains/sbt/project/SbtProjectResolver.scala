package org.jetbrains.sbt
package project

import java.io.File

import com.intellij.openapi.externalSystem.model.project._
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationEvent, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.model.{DataNode, ExternalSystemException}
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.sbt.project.data._
import org.jetbrains.sbt.project.module.SbtModuleType
import org.jetbrains.sbt.project.settings._
import org.jetbrains.sbt.project.structure._
import org.jetbrains.sbt.resolvers.SbtResolver

/**
 * @author Pavel Fatin
 */
class SbtProjectResolver extends ExternalSystemProjectResolver[SbtExecutionSettings] with ExternalSourceRootResolution {

  private var runner: SbtRunner = null

  def resolveProjectInfo(id: ExternalSystemTaskId, projectPath: String, isPreview: Boolean, settings: SbtExecutionSettings, listener: ExternalSystemTaskNotificationListener): DataNode[ProjectData] = {
    val root = {
      val file = new File(projectPath)
      if (file.isDirectory) file.getPath else file.getParent
    }

    runner = new SbtRunner(settings.vmExecutable, settings.vmOptions, settings.environment,
                           settings.customLauncher, settings.customSbtStructureDir)

    var warnings = new StringBuilder()

    val xml = runner.read(new File(root), !isPreview, settings.resolveClassifiers, settings.resolveSbtClassifiers) { message =>
      if (message.startsWith("[error] ") || message.startsWith("[warn] ")) {
        warnings ++= message
      }

      listener.onStatusChange(new ExternalSystemTaskNotificationEvent(id, message.trim))
    } match {
      case Left(errors) => errors match {
        case _ : SbtRunner.ImportCancelledException => return null
        case _ => throw new ExternalSystemException(errors)
      }
      case Right(node) => node
    }

    if (warnings.nonEmpty) {
      listener.onTaskOutput(id, WarningMessage(warnings.toString), false)
    }

    val data = StructureParser.parse(xml, new File(System.getProperty("user.home")))

    convert(root, data, settings.jdk).toDataNode
  }

  private def convert(root: String, data: Structure, jdk: Option[String]): Node[ProjectData] = {
    val projects = data.projects
    val project = data.projects.headOption.getOrElse(throw new RuntimeException("No root project found"))
    val projectNode = new ProjectNode(project.name, root, root)

    val javacOptions = project.java.map(_.options).getOrElse(Seq.empty)
    val sbtVersion = data.sbtVersion
    val projectJdk =
      if (project.android.isDefined)
        Some(ScalaProjectData.Android(project.android.get.version))
      else
        jdk map ScalaProjectData.Jdk

    projectNode.add(new ScalaProjectNode(projectJdk, javacOptions, sbtVersion, FileUtil.toSystemIndependentName(root)))

    project.play2 map {
      case play2Data => projectNode.add(new Play2ProjectNode(play2Data.keys))
    }

    val libraryNodes = createLibraries(data, projects)
    projectNode.addAll(libraryNodes)

    val moduleFilesDirectory = new File(root + "/" + Sbt.ModulesDirectory)
    val moduleNodes = createModules(projects, libraryNodes, moduleFilesDirectory)
    projectNode.addAll(moduleNodes)

    createModuleDependencies(projects, moduleNodes)

    val projectToModuleNode: Map[Project, ModuleNode] = projects.zip(moduleNodes).toMap
    val sharedSourceModules = createSharedSourceModules(projectToModuleNode, libraryNodes, moduleFilesDirectory)
    projectNode.addAll(sharedSourceModules)

    projectNode.addAll(projects.map(createBuildModule(_, moduleFilesDirectory, data.localCachePath)))
    projectNode
  }

  def createModuleDependencies(projects: Seq[Project], moduleNodes: Seq[ModuleNode]): Unit = {
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
  }

  def createModules(projects: Seq[Project], libraryNodes: Seq[LibraryNode], moduleFilesDirectory: File): Seq[ModuleNode] = {
    val unmanagedSourcesAndDocsLibrary = libraryNodes.map(_.data).find(_.getExternalName == Sbt.UnmanagedSourcesAndDocsName)
    projects.map { project =>
      val moduleNode = createModule(project, moduleFilesDirectory)
      moduleNode.add(createContentRoot(project))
      moduleNode.addAll(createLibraryDependencies(project.dependencies.modules)(moduleNode, libraryNodes.map(_.data)))
      moduleNode.addAll(project.scala.map(createScalaSdk(project, _)).toSeq)
      moduleNode.addAll(project.android.map(createFacet(project, _)).toSeq)
      moduleNode.addAll(createUnmanagedDependencies(project.dependencies.jars)(moduleNode))
      unmanagedSourcesAndDocsLibrary foreach { lib =>
        val dependency = new LibraryDependencyNode(moduleNode, lib, LibraryLevel.MODULE)
        dependency.setScope(DependencyScope.COMPILE)
        moduleNode.add(dependency)
      }
      moduleNode
    }
  }

  def createLibraries(data: Structure, projects: Seq[Project]): Seq[LibraryNode] = {
    val repositoryModules = data.repository.map(_.modules).getOrElse(Seq.empty)
    val (modulesWithoutBinaries, modulesWithBinaries) = repositoryModules.partition(_.binaries.isEmpty)
    val otherModuleIds = projects.flatMap(_.dependencies.modules.map(_.id)).toSet --
            repositoryModules.map(_.id).toSet

    val libs = modulesWithBinaries.map(createResolvedLibrary) ++ otherModuleIds.map(createUnresolvedLibrary)

    if (modulesWithoutBinaries.isEmpty) return libs

    val unmanagedSourceLibrary = new LibraryNode(Sbt.UnmanagedSourcesAndDocsName, true)
    unmanagedSourceLibrary.addPaths(LibraryPathType.DOC, modulesWithoutBinaries.flatMap(_.docs).map(_.path))
    unmanagedSourceLibrary.addPaths(LibraryPathType.SOURCE, modulesWithoutBinaries.flatMap(_.sources).map(_.path))
    libs :+ unmanagedSourceLibrary
  }

  private def createScalaSdk(project: Project, scala: Scala): ScalaSdkNode = {
    val basePackage = Some(project.organization).filter(_.contains(".")).mkString

    val compilerClasspath = scala.compilerJar +: scala.libraryJar +: scala.extraJars

    new ScalaSdkNode(scala.version, basePackage, compilerClasspath, scala.options)
  }

  private def createFacet(project: Project, android: Android): AndroidFacetNode = {
    new AndroidFacetNode(android.version, android.manifestFile, android.apkPath,
                         android.resPath, android.assetsPath, android.genPath, android.libsPath,
                         android.isLibrary, android.proguardConfig)
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

  private def nameFor(id: ModuleId) =
    s"${id.organization}:${id.name}:${id.revision}" + id.classifier.map(":"+_).getOrElse("") + s":${id.artifactType}"

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

    getExcludedTargetDirs(project).foreach { path =>
      result.storePath(ExternalSystemSourceType.EXCLUDED, path.path)
    }

    result
  }

  // We cannot always exclude the whole ./target/ directory because of
  // the generated sources, so we resort to an heuristics.
  private def getExcludedTargetDirs(project: Project): List[File] = {
    val managedDirectories = project.configurations
            .flatMap(configuration => configuration.sources ++ configuration.resources)
            .filter(_.managed)
            .map(_.file)

    val defaultNames = Set("main", "test")

    val relevantDirectories = managedDirectories.filter(file => file.exists || !defaultNames.contains(file.getName))
    def isRelevant(f: File): Boolean = !relevantDirectories.forall(_.isOutsideOf(f))

    if (isRelevant(project.target)) {
      // If we can't exclude the target directory, go one level deeper (which may hit resolution-cache and streams)
      Option(project.target.listFiles()).toList.flatten.filter {
        child => child.isDirectory && !isRelevant(child)
      }
    } else List(project.target)
  }

  private def createBuildModule(project: Project, moduleFilesDirectory: File, localCachePath: Option[String]): ModuleNode = {
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

    result.add(createSbtModuleData(project, localCachePath))

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

  def createSbtModuleData(project: Project, localCachePath: Option[String]): SbtModuleNode = {
    val imports = project.build.imports.flatMap(_.trim.substring(7).split(", "))
    val resolvers = project.resolvers map { r => new SbtResolver(SbtResolver.Kind.Maven, r.name, r.root) }
    new SbtModuleNode(imports, resolvers + SbtResolver.localCacheResolver(localCachePath))
  }

  private def validRootPathsIn(project: Project, scope: String)
                              (selector: Configuration => Seq[Directory]): Seq[String] = {
    project.configurations
            .find(_.id == scope)
            .map(selector)
            .getOrElse(Seq.empty)
            .map(_.file)
            .filter(!_.isOutsideOf(project.base))
            .map(_.path)
  }

  protected def createLibraryDependencies(dependencies: Seq[ModuleDependency])(moduleData: ModuleData, libraries: Seq[LibraryData]): Seq[LibraryDependencyNode] = {
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

  protected def scopeFor(configurations: Seq[String]): DependencyScope = {
    val ids = configurations.toSet

    if (ids.contains("compile"))
      DependencyScope.COMPILE
    else if (ids.contains("runtime"))
      DependencyScope.RUNTIME
    else if (ids.contains("test"))
      DependencyScope.TEST
    else if (ids.contains("provided"))
      DependencyScope.PROVIDED
    else
      DependencyScope.COMPILE
  }

  def cancelTask(taskId: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener) = {
    if (runner != null)
      runner.cancel()
    false
  }
}
