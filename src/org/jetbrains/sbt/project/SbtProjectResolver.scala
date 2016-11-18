package org.jetbrains.sbt
package project

import java.io.File

import com.intellij.openapi.externalSystem.model.project.{ProjectData => ESProjectData, _}
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationEvent, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.model.{DataNode, ExternalSystemException}
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.project.SbtProjectResolver._
import org.jetbrains.sbt.project.data._
import org.jetbrains.sbt.project.module.SbtModuleType
import org.jetbrains.sbt.project.settings._
import org.jetbrains.sbt.project.structure._
import org.jetbrains.sbt.resolvers.{SbtMavenResolver, SbtResolver}
import org.jetbrains.sbt.structure.XmlSerializer._
import org.jetbrains.sbt.{structure => sbtStructure}

import scala.util.{Failure, Success}

/**
 * @author Pavel Fatin
 */
class SbtProjectResolver extends ExternalSystemProjectResolver[SbtExecutionSettings] with ExternalSourceRootResolution {

  private var runner: SbtRunner = null

  protected var taskListener: TaskListener = SilentTaskListener

  override def resolveProjectInfo(id: ExternalSystemTaskId,
                         wrongProjectPathDontUseIt: String,
                         isPreview: Boolean,
                         settings: SbtExecutionSettings,
                         listener: ExternalSystemTaskNotificationListener): DataNode[ESProjectData] = {
    val root = {
      val file = new File(settings.realProjectPath)
      if (file.isDirectory) file.getPath else file.getParent
    }

    runner = new SbtRunner(settings.vmExecutable, settings.vmOptions, settings.environment,
                           settings.customLauncher, settings.customSbtStructureFile)

    taskListener = new ExternalTaskListener(listener, id)

    var warnings = new StringBuilder()

    val xml = runner.read(new File(root), !isPreview, settings.resolveClassifiers,
        settings.resolveJavadocs, settings.resolveSbtClassifiers) { message =>
      if (message.startsWith("[error] ") || message.startsWith("[warn] ")) {
        warnings ++= message
      }

      listener.onStatusChange(new ExternalSystemTaskNotificationEvent(id, message.trim))
    } match {
      case Failure(errors) => errors match {
        case _ : SbtRunner.ImportCancelledException => return null
        case _ => throw new ExternalSystemException(errors)
      }
      case Success(node) => node
    }

    if (warnings.nonEmpty) {
      listener.onTaskOutput(id, WarningMessage(warnings.toString), false)
    }

    val data = xml.deserialize[sbtStructure.StructureData].right.get

    convert(root, data, settings.jdk).toDataNode
  }

  private def convert(root: String, data: sbtStructure.StructureData, settingsJdk: Option[String]): Node[ESProjectData] = {
    val projects = data.projects
    val project: sbtStructure.ProjectData = data.projects.find(p => FileUtil.filesEqual(p.base, new File(root)))
      .orElse(data.projects.headOption)
      .getOrElse(throw new RuntimeException("No root project found"))
    val projectNode = new ProjectNode(project.name, root, root)

    val basePackages = projects.flatMap(_.basePackages).distinct
    val javacOptions = project.java.map(_.options).getOrElse(Seq.empty)
    val sbtVersion = data.sbtVersion

    val projectJdk = chooseJdk(project, settingsJdk)

    projectNode.add(new SbtProjectNode(basePackages, projectJdk, javacOptions, sbtVersion, root))

    val newPlay2Data = projects.flatMap(p => p.play2.map(d => (p.id, p.base, d)))
    projectNode.add(new Play2ProjectNode(Play2OldStructureAdapter(newPlay2Data)))

    val libraryNodes = createLibraries(data, projects)
    projectNode.addAll(libraryNodes)

    val moduleFilesDirectory = new File(root + "/" + Sbt.ModulesDirectory)
    val moduleNodes = createModules(projects, libraryNodes, moduleFilesDirectory)
    projectNode.addAll(moduleNodes)

    createModuleDependencies(projects, moduleNodes)

    val projectToModuleNode: Map[sbtStructure.ProjectData, ModuleNode] = projects.zip(moduleNodes).toMap
    val sharedSourceModules = createSharedSourceModules(projectToModuleNode, libraryNodes, moduleFilesDirectory)
    projectNode.addAll(sharedSourceModules)

    projectNode.addAll(projects.map(createBuildModule(_, moduleFilesDirectory, data.localCachePath)))
    projectNode
  }

  /** Choose a project jdk based on information from sbt settings and IDE.
    * More specific settings from sbt are preferred over IDE settings, on the assumption that the sbt project definition
    * is what is more likely to be under source control.
    */
  private def chooseJdk(project: sbtStructure.ProjectData, defaultJdk: Option[String]) = {
    // TODO put some of this logic elsewhere in resolving process?
    val androidSdk = project.android.map(android => Android(android.targetVersion))
    val jdkHomeInSbtProject = project.java.flatMap(_.home).map(JdkByHome)

    // default either from project structure or initial import settings
    val default = defaultJdk.map(JdkByName)

    androidSdk
      .orElse(jdkHomeInSbtProject)
      .orElse(default)
  }

  def createModuleDependencies(projects: Seq[sbtStructure.ProjectData], moduleNodes: Seq[ModuleNode]): Unit = {
    projects.zip(moduleNodes).foreach { case (moduleProject, moduleNode) =>
      moduleProject.dependencies.projects.foreach { dependencyId =>
        val dependency = moduleNodes.find(_.getId == dependencyId.project).getOrElse(
          throw new ExternalSystemException("Cannot find project dependency: " + dependencyId.project))
        val data = new ModuleDependencyNode(moduleNode, dependency)
        data.setScope(scopeFor(dependencyId.configuration))
        data.setExported(true)
        moduleNode.add(data)
      }
    }
  }

  def createModules(projects: Seq[sbtStructure.ProjectData], libraryNodes: Seq[LibraryNode], moduleFilesDirectory: File): Seq[ModuleNode] = {
    val unmanagedSourcesAndDocsLibrary = libraryNodes.map(_.data).find(_.getExternalName == Sbt.UnmanagedSourcesAndDocsName)
    projects.map { project =>
      val moduleNode = createModule(project, moduleFilesDirectory)
      val contentRootNode = createContentRoot(project)
      project.android.foreach(a => a.apklibs.foreach(addApklibDirs(contentRootNode, _)))
      moduleNode.add(contentRootNode)
      moduleNode.addAll(createLibraryDependencies(project.dependencies.modules)(moduleNode, libraryNodes.map(_.data)))
      moduleNode.add(createModuleExtData(project))
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

  def createLibraries(data: sbtStructure.StructureData, projects: Seq[sbtStructure.ProjectData]): Seq[LibraryNode] = {
    val repositoryModules = data.repository.map(_.modules).getOrElse(Seq.empty)
    val (modulesWithoutBinaries, modulesWithBinaries) = repositoryModules.partition(_.binaries.isEmpty)
    val otherModuleIds = projects.flatMap(_.dependencies.modules.map(_.id)).toSet --
            repositoryModules.map(_.id).toSet

    val libs = modulesWithBinaries.map(createResolvedLibrary) ++ otherModuleIds.map(createUnresolvedLibrary)

    val modulesWithDocumentation = modulesWithoutBinaries.filter(m => m.docs.nonEmpty || m.sources.nonEmpty)
    if (modulesWithDocumentation.isEmpty) return libs

    val unmanagedSourceLibrary = new LibraryNode(Sbt.UnmanagedSourcesAndDocsName, true)
    unmanagedSourceLibrary.addPaths(LibraryPathType.DOC, modulesWithDocumentation.flatMap(_.docs).map(_.path))
    unmanagedSourceLibrary.addPaths(LibraryPathType.SOURCE, modulesWithDocumentation.flatMap(_.sources).map(_.path))
    libs :+ unmanagedSourceLibrary
  }

  private def createModuleExtData(project: sbtStructure.ProjectData): ModuleExtNode = {
    val scalaVersion = project.scala.map(s => Version(s.version))
    val scalacClasspath = project.scala.fold(Seq.empty[File])(s => s.compilerJar +: s.libraryJar +: s.extraJars)
    val scalacOptions = project.scala.fold(Seq.empty[String])(_.options)
    val javacOptions = project.java.fold(Seq.empty[String])(_.options)
    val jdk = project.android.map(android => Android(android.targetVersion))
      .orElse(project.java.flatMap(java => java.home.map(JdkByHome)))
    new ModuleExtNode(scalaVersion, scalacClasspath, scalacOptions, jdk, javacOptions)
  }

  private def createFacet(project: sbtStructure.ProjectData, android: sbtStructure.AndroidData): AndroidFacetNode = {
    new AndroidFacetNode(android.targetVersion, android.manifest, android.apk,
                         android.res, android.assets, android.gen, android.libs,
                         android.isLibrary, android.proguardConfig)
  }

  private def createUnresolvedLibrary(moduleId: sbtStructure.ModuleIdentifier): LibraryNode = {
    val module = sbtStructure.ModuleData(moduleId, Set.empty, Set.empty, Set.empty)
    createLibrary(module, resolved = false)
  }

  private def createResolvedLibrary(module: sbtStructure.ModuleData): LibraryNode = {
    createLibrary(module, resolved = true)
  }

  private def createLibrary(module: sbtStructure.ModuleData, resolved: Boolean): LibraryNode = {
    val result = new LibraryNode(nameFor(module.id), resolved)
    result.addPaths(LibraryPathType.BINARY, module.binaries.map(_.path).toSeq)
    result.addPaths(LibraryPathType.DOC, module.docs.map(_.path).toSeq)
    result.addPaths(LibraryPathType.SOURCE, module.sources.map(_.path).toSeq)
    result
  }

  private def nameFor(id: sbtStructure.ModuleIdentifier) = {
    val classifierOption = if (id.classifier.isEmpty) None else Some(id.classifier)
    s"${id.organization}:${id.name}:${id.revision}" + classifierOption.map(":"+_).getOrElse("") + s":${id.artifactType}"
  }

  private def createModule(project: sbtStructure.ProjectData, moduleFilesDirectory: File): ModuleNode = {
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

  private def createContentRoot(project: sbtStructure.ProjectData): ContentRootNode = {
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
  private def getExcludedTargetDirs(project: sbtStructure.ProjectData): Seq[File] = {
    val extractedExcludes = project.configurations.flatMap(_.excludes)
    if (extractedExcludes.nonEmpty)
      return extractedExcludes.distinct

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

  private def createBuildModule(project: sbtStructure.ProjectData, moduleFilesDirectory: File, localCachePath: Option[String]): ModuleNode = {
    val id = project.id + Sbt.BuildModuleSuffix
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

  def createSbtModuleData(project: sbtStructure.ProjectData, localCachePath: Option[String]): SbtModuleNode = {
    val imports = project.build.imports.flatMap(_.trim.substring(7).split(", "))
    val resolvers = project.resolvers map { r => new SbtMavenResolver(r.name, r.root).asInstanceOf[SbtResolver] }
    new SbtModuleNode(imports, resolvers + SbtResolver.localCacheResolver(localCachePath))
  }

  private def validRootPathsIn(project: sbtStructure.ProjectData, scope: String)
                              (selector: sbtStructure.ConfigurationData => Seq[sbtStructure.DirectoryData]): Seq[String] = {
    project.configurations
            .find(_.id == scope)
            .map(selector)
            .getOrElse(Seq.empty)
            .map(_.file)
            .filter(!_.isOutsideOf(project.base))
            .map(_.path)
  }

  protected def createLibraryDependencies(dependencies: Seq[sbtStructure.ModuleDependencyData])
      (moduleData: ModuleData, libraries: Seq[LibraryData]): Seq[LibraryDependencyNode] = {
    dependencies.map { dependency =>
      val name = nameFor(dependency.id)
      val library = libraries.find(_.getExternalName == name).getOrElse(
        throw new ExternalSystemException("Library not found: " + name))
      val data = new LibraryDependencyNode(moduleData, library, LibraryLevel.PROJECT)
      data.setScope(scopeFor(dependency.configurations))
      data
    }
  }

  private def createUnmanagedDependencies(dependencies: Seq[sbtStructure.JarDependencyData])
      (moduleData: ModuleData): Seq[LibraryDependencyNode] = {
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

  private def addApklibDirs(contentRootNode: ContentRootNode, apklib: sbtStructure.ApkLib): Unit = {
    contentRootNode.storePath(ExternalSystemSourceType.SOURCE, apklib.sources.canonicalPath)
    contentRootNode.storePath(ExternalSystemSourceType.SOURCE_GENERATED, apklib.gen.canonicalPath)
    contentRootNode.storePath(ExternalSystemSourceType.RESOURCE, apklib.resources.canonicalPath)
  }

  protected def scopeFor(configurations: Seq[sbtStructure.Configuration]): DependencyScope = {
    val ids = configurations.toSet

    if (ids.contains(sbtStructure.Configuration.Compile))
      DependencyScope.COMPILE
    else if (ids.contains(sbtStructure.Configuration.Runtime))
      DependencyScope.RUNTIME
    else if (ids.contains(sbtStructure.Configuration.Test))
      DependencyScope.TEST
    else if (ids.contains(sbtStructure.Configuration.Provided))
      DependencyScope.PROVIDED
    else
      DependencyScope.COMPILE
  }

  def cancelTask(taskId: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener): Boolean = {
    if (runner != null)
      runner.cancel()
    false
  }
}

object SbtProjectResolver {
  trait TaskListener {
    def onTaskOutput(message: String, stdOut: Boolean): Unit
  }

  object SilentTaskListener extends TaskListener {
    override def onTaskOutput(message: String, stdOut: Boolean): Unit = {}
  }

  class ExternalTaskListener(
    val listener: ExternalSystemTaskNotificationListener,
    val taskId: ExternalSystemTaskId)
      extends TaskListener {
    def onTaskOutput(message: String, stdOut: Boolean): Unit =
      listener.onTaskOutput(taskId, message, stdOut)
  }

}
