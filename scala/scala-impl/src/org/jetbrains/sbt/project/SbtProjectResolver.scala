package org.jetbrains.sbt
package project

import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.project.{ProjectData => ESProjectData, _}
import com.intellij.openapi.externalSystem.model.task.event._
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.model.{DataNode, ExternalSystemException}
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.annotations.{NonNls, Nullable}
import org.jetbrains.plugins.scala._
import org.jetbrains.plugins.scala.build._
import org.jetbrains.plugins.scala.compiler.data.serialization.extensions.EitherExt
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.plugins.scala.project.external.{AndroidJdk, JdkByHome, JdkByName, SdkReference}
import org.jetbrains.plugins.scala.util.ScalaNotificationGroups
import org.jetbrains.sbt.SbtUtil._
import org.jetbrains.sbt.project.SbtProjectResolver._
import org.jetbrains.sbt.project.data._
import org.jetbrains.sbt.project.module.SbtModuleType
import org.jetbrains.sbt.project.settings._
import org.jetbrains.sbt.project.structure._
import org.jetbrains.sbt.resolvers.{SbtMavenResolver, SbtResolver}
import org.jetbrains.sbt.structure.XmlSerializer._
import org.jetbrains.sbt.structure.{BuildData, ConfigurationData, DependencyData, DirectoryData, JavaData, ProjectData}
import org.jetbrains.sbt.{structure => sbtStructure}

import java.io.{File, FileNotFoundException}
import java.util.{Locale, UUID}
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Random, Success, Try}
import scala.xml.{Elem, XML}

/**
 * @author Pavel Fatin
 */
class SbtProjectResolver extends ExternalSystemProjectResolver[SbtExecutionSettings] with ExternalSourceRootResolution {

  private val log = Logger.getInstance(getClass)

  @volatile private var activeProcessDumper: Option[SbtStructureDump] = None

  override def resolveProjectInfo(taskId: ExternalSystemTaskId,
                                  wrongProjectPathDontUseIt: String,
                                  isPreview: Boolean,
                                  settings: SbtExecutionSettings,
                                  listener: ExternalSystemTaskNotificationListener): DataNode[ESProjectData] = {


    val projectRoot = {
      val file = new File(settings.realProjectPath)
      if (file.isDirectory) file else file.getParentFile
    }

    val sbtLauncher = settings.customLauncher.getOrElse(getDefaultLauncher)
    val sbtVersion = detectSbtVersion(projectRoot, sbtLauncher)

    if (isPreview) dummyProject(projectRoot, settings, sbtVersion).toDataNode
    else importProject(taskId, settings, projectRoot, sbtLauncher, sbtVersion, listener)

  }

  private def importProject(taskId: ExternalSystemTaskId,
                            settings: SbtExecutionSettings,
                            projectRoot: File,
                            sbtLauncher: File,
                            @NonNls sbtVersion: String,
                            notifications: ExternalSystemTaskNotificationListener): DataNode[ESProjectData] = {

    @NonNls val importTaskId = s"import:${UUID.randomUUID()}"
    val importTaskDescriptor =
      new TaskOperationDescriptorImpl(SbtBundle.message("sbt.import.to.intellij.project.model"), System.currentTimeMillis(), "project-model-import")

    val esReporter = new ExternalSystemNotificationReporter(projectRoot.getAbsolutePath, taskId, notifications)
    implicit val reporter: BuildReporter = if (isUnitTestMode) {
      val logReporter = new LogReporter
      new CompositeReporter(esReporter, logReporter)
    } else esReporter

    val structureDump = dumpStructure(projectRoot, sbtLauncher, Version(sbtVersion), settings, taskId.findProject())

    // side-effecty status reporting
    structureDump.foreach { case (_, messages) =>
      val convertStartEvent = new ExternalSystemStartEventImpl(importTaskId, null, importTaskDescriptor)
      val event = new ExternalSystemTaskExecutionEvent(taskId, convertStartEvent)
      notifications.onStatusChange(event)
    }

    val conversionResult = structureDump
      .map { case (elem, _) =>
        val data = elem.deserialize[sbtStructure.StructureData].getRight
        convert(normalizePath(projectRoot), data, settings.jdk).toDataNode
      }
      .recoverWith {
        case ImportCancelledException(cause) =>
          val causeMessage = if (cause != null) cause.getMessage else SbtBundle.message("sbt.unknown.cause")

          // notify user if project exists already
          val projectOpt = ProjectManager.getInstance().getOpenProjects.find(p => FileUtil.pathsEqual(p.getBasePath, projectRoot.getCanonicalPath))
          projectOpt.foreach { p =>
            val notification = ScalaNotificationGroups.balloonGroup.createNotification(SbtBundle.message("sbt.import.cancelled", causeMessage), NotificationType.INFORMATION)
            notification.notify(p)
          }

          log.info("sbt import cancelled", cause)
          // sorry, ExternalSystem expects a null when resolving is not possible
          Success(null)
        case x: Exception =>
          Failure(new ExternalSystemException(x))
      }

    // more side-effecty reporting
    conversionResult.transform (
      _ => Success(new SuccessResultImpl(0, System.currentTimeMillis(), true)), /* TODO starttime*/
      x => Success(
        new FailureResultImpl(0, System.currentTimeMillis(),
          List.empty[com.intellij.openapi.externalSystem.model.task.event.Failure].asJava // TODO error list
        )
      )
    ).foreach { result =>
      val convertFinishedEvent = new ExternalSystemFinishEventImpl[TaskOperationDescriptor](
        importTaskId, null, importTaskDescriptor, result
      )
      val event = new ExternalSystemTaskExecutionEvent(taskId, convertFinishedEvent)
      notifications.onStatusChange(event)
    }

    conversionResult.get // ok to throw here, that's the way ExternalSystem likes it

  }

  private def dumpStructure(projectRoot: File,
                            sbtLauncher: File,
                            sbtVersion: Version,
                            settings:SbtExecutionSettings,
                            @Nullable project: Project
                           )(implicit reporter: BuildReporter): Try[(Elem, BuildMessages)] = {

    val useShellImport = settings.useShellForImport && shellImportSupported(sbtVersion) && project != null
    val options = dumpOptions(settings)

    if (!sbtLauncher.isFile) {
      val error = SbtBundle.message("sbt.launcher.not.found", sbtLauncher.getCanonicalPath)
      Failure(new FileNotFoundException(error))
    } else if (!importSupported(sbtVersion)) {
      val message = SbtBundle.message("sbt.sincesbtversion.required", sinceSbtVersion)
      Failure(new UnsupportedOperationException(message))
    }
    else usingTempFile("sbt-structure", Some(".xml")) { structureFile =>
      val structureFilePath = normalizePath(structureFile)

      val dumper = new SbtStructureDump()
      activeProcessDumper = Option(dumper)

      val messageResult: Try[BuildMessages] = {
        if (useShellImport) {
          val messagesF = dumper.dumpFromShell(project, structureFilePath, options, reporter)
          Try(Await.result(messagesF, Duration.Inf)) // TODO some kind of timeout / cancel mechanism
        }
        else {
          val sbtStructureJar = settings
            .customSbtStructureFile
            .orElse(SbtUtil.getSbtStructureJar(sbtVersion))
            .getOrElse(throw new ExternalSystemException(s"Could not find sbt-structure-extractor for sbt version $sbtVersion"))

          val structureFilePath = normalizePath(structureFile)

          // TODO add error/warning messages during dump, report directly
          dumper.dumpFromProcess(
            projectRoot, structureFilePath, options,
            settings.vmExecutable, settings.vmOptions, settings.environment,
            sbtLauncher, sbtStructureJar)
        }
      }
      activeProcessDumper = None

      val result: Try[(Elem, BuildMessages)] = messageResult.flatMap { messages =>
        val tried = {
          def failure(reason: String): Failure[(Elem, BuildMessages)] = {
            val message = SbtBundle.message("sbt.import.extracting.structure.failed") + s", reason: ${reason}"
            Failure(new Exception(message))
          }
          if (messages.status != BuildMessages.OK)
            failure(s"not ok build status: ${messages.status} (${messages})")
          else if (!structureFile.isFile)
            failure(s"structure file is not a file")
          else if (structureFile.length <= 0)
            failure(s"structure file is empty")
          else Try {
            val elem = XML.load(structureFile.toURI.toURL)
            (elem, messages)
          }
        }

        tried.recoverWith { case error =>
          val exceptions = messages.exceptions.map(_.getLocalizedMessage).mkString("\n")
          val errorMsgs = messages.errors.map(_.getMessage).mkString("\n")
          val message = error.getMessage + "\n" +
            exceptions + (if (exceptions.nonEmpty) "\n" else "") +
            errorMsgs
          Failure(new Exception(message, error.getCause))
        }
      }
      if (result.isFailure) {
        val processOutput = dumper.processOutput.mkString
        // exception is logged in other places
        log.debug(s"failed to dump sbt structure, sbt process output:\n$processOutput")
      }
      result
    }
  }

  private def dumpOptions(settings: SbtExecutionSettings): Seq[String] = {
      Seq("download") ++
      settings.resolveClassifiers.seq("resolveClassifiers") ++
      settings.resolveJavadocs.seq("resolveJavadocs") ++
      settings.resolveSbtClassifiers.seq("resolveSbtClassifiers")
  }

  /**
    * Create project preview without using sbt, since sbt import can fail and users would have to do a manual edit of the project.
    * Also sbt boot makes the whole process way too slow.
    */
  private def dummyProject(projectRoot: File, settings: SbtExecutionSettings, sbtVersion: String): Node[ESProjectData] = {

    // TODO add default scala sdk and sbt libs (newest versions or so)

    val projectPath = projectRoot.getAbsolutePath
    val projectName = normalizeModuleId(projectRoot.getName)
    val projectTmpName = projectName + "_" + Random.nextInt(10000)
    val sourceDir = new File(projectRoot, "src/main/scala")
    val classDir = new File(projectRoot, "target/dummy")

    val dummyConfigurationData = ConfigurationData("compile", Seq(DirectoryData(sourceDir, managed = false)), Seq.empty, Seq.empty, classDir)
    val dummyJavaData = JavaData(None, Seq.empty)
    val dummyDependencyData = DependencyData(Seq.empty, Seq.empty, Seq.empty)
    val dummyRootProject = ProjectData(
      projectTmpName, projectRoot.toURI, projectTmpName, s"org.$projectName", "0.0", projectRoot, None, Seq.empty,
      new File(projectRoot, "target"), Seq(dummyConfigurationData), Option(dummyJavaData), None, None,
      dummyDependencyData, Set.empty, None, Seq.empty, Seq.empty, Seq.empty
    )

    val projects = Seq(dummyRootProject)

    val projectNode = new ProjectNode(projectName, projectPath, projectPath)
    val libraryNodes = Seq.empty[LibraryNode]
    val moduleFilesDirectory = new File(projectPath, Sbt.ModulesDirectory)
    val projectToModule = createModules(projects, libraryNodes, moduleFilesDirectory)

    val dummySbtProjectData = SbtProjectData(settings.jdk.map(JdkByName), sbtVersion, projectPath)
    projectNode.add(new SbtProjectNode(dummySbtProjectData))
    projectNode.addAll(projectToModule.values)

    val dummyBuildData = BuildData(projectRoot.toURI, Seq.empty, Seq.empty, Seq.empty, Seq.empty)
    val buildModule = createBuildModule(dummyBuildData, projects, moduleFilesDirectory, None)
    projectNode.add(buildModule)

    projectNode
  }

  /**
    * This implementation is the same as in sbt.Project.normalizeModuleId to avoid inconsistencies in the import process.
    * Normalize a String so that it is suitable for use as a dependency management module identifier.
    * This is a best effort implementation, since valid characters are not documented or consistent.
    */
  private def normalizeModuleId(s: String) =
    s.toLowerCase(Locale.ENGLISH)
      .replaceAll("""\W+""", "-")

  private def convert(root: String,
                      data: sbtStructure.StructureData,
                      settingsJdk: Option[String]): Node[ESProjectData] = {
    val projects: Seq[sbtStructure.ProjectData] = data.projects
    val rootProject: sbtStructure.ProjectData =
      projects.find(p => FileUtil.filesEqual(p.base, new File(root)))
        .orElse(projects.headOption)
        .getOrElse(throw new RuntimeException("No root project found"))
    val projectNode = new ProjectNode(rootProject.name, root, root)

    val projectJdk = chooseJdk(rootProject, settingsJdk)

    projectNode.add(new SbtProjectNode(SbtProjectData(projectJdk, data.sbtVersion, root)))

    val newPlay2Data = projects.flatMap(p => p.play2.map(d => (p.id, p.base, d)))
    projectNode.add(new Play2ProjectNode(Play2OldStructureAdapter(newPlay2Data)))

    val libraryNodes = createLibraries(data, projects)
    projectNode.addAll(libraryNodes)

    val moduleFilesDirectory = new File(root, Sbt.ModulesDirectory)
    val projectToModule = createModules(projects, libraryNodes, moduleFilesDirectory)
    projectNode.addAll(projectToModule.values)

    val sharedSourceModules = createSharedSourceModules(projectToModule, libraryNodes, moduleFilesDirectory)
    projectNode.addAll(sharedSourceModules)

    val buildModuleForProject: BuildData => ModuleNode = createBuildModule(_, projects, moduleFilesDirectory, data.localCachePath.map(_.getCanonicalPath))
    val buildModules = data.builds.map(buildModuleForProject)

    if (buildModules.size > 1) {
      buildModules.foreach(_.setIdeModuleGroup(Array("sbt-build-modules")))
    }

    projectNode.addAll(buildModules)
    projectNode
  }

  /** Choose a project jdk based on information from sbt settings and IDE.
    * More specific settings from sbt are preferred over IDE settings, on the assumption that the sbt project definition
    * is what is more likely to be under source control.
    */
  private def chooseJdk(project: sbtStructure.ProjectData, defaultJdk: Option[String]): Option[SdkReference] = {
    // TODO put some of this logic elsewhere in resolving process?
    val androidSdk = project.android.map(android => AndroidJdk(android.targetVersion))
    val jdkHomeInSbtProject = project.java.flatMap(_.home).map(JdkByHome)

    // default either from project structure or initial import settings
    val default = defaultJdk.map(JdkByName)

    androidSdk
      .orElse(jdkHomeInSbtProject)
      .orElse(default)
  }

  private def createModuleDependencies(projectToModule: Map[ProjectData,ModuleNode]): Unit = {
    projectToModule.foreach { case (moduleProject, moduleNode) =>
      moduleProject.dependencies.projects.foreach { dependencyId =>
        val dependency =
          projectToModule.values
            .find(_.getId == ModuleNode.combinedId(dependencyId.project, dependencyId.buildURI))
            .getOrElse(throw new ExternalSystemException("Cannot find project dependency: " + dependencyId.project))
        val data = new ModuleDependencyNode(moduleNode, dependency)
        data.setScope(scopeFor(dependencyId.configuration))
        data.setExported(true)
        moduleNode.add(data)
      }
    }
  }

  private def createModules(projects: Seq[sbtStructure.ProjectData], libraryNodes: Seq[LibraryNode], moduleFilesDirectory: File): Map[ProjectData,ModuleNode] = {
    val unmanagedSourcesAndDocsLibrary = libraryNodes.map(_.data).find(_.getExternalName == Sbt.UnmanagedSourcesAndDocsName)

    val nameToProjects = projects.groupBy(_.name)
    val namesAreUnique = nameToProjects.size == projects.size

    val projectToModule = projects.map { project =>

      val moduleName =
        if (namesAreUnique) project.name
        else project.id

      val groupName =
        if (nameToProjects(project.name).size > 1) Array(project.name)
        else null

      val moduleNode = createModule(project, moduleFilesDirectory, moduleName)

      moduleNode.setIdeModuleGroup(groupName)

      val contentRootNode = createContentRoot(project)
      project.android.foreach(a => a.apklibs.foreach(addApklibDirs(contentRootNode, _)))
      moduleNode.add(contentRootNode)
      moduleNode.addAll(createLibraryDependencies(project.dependencies.modules)(moduleNode, libraryNodes.map(_.data)))
      moduleNode.add(createModuleExtData(project))
      moduleNode.add(new SbtModuleNode(SbtModuleData(project.id, project.buildURI)))
      moduleNode.addAll(createTaskData(project))
      moduleNode.addAll(createSettingData(project))
      moduleNode.addAll(createCommandData(project))
      moduleNode.addAll(project.android.map(createFacet(project, _)).toSeq)
      moduleNode.addAll(createUnmanagedDependencies(project.dependencies.jars)(moduleNode))
      unmanagedSourcesAndDocsLibrary foreach { lib =>
        val dependency = new LibraryDependencyNode(moduleNode, lib, LibraryLevel.MODULE)
        dependency.setScope(DependencyScope.COMPILE)
        moduleNode.add(dependency)
      }
      (project,moduleNode)
    }

    val projectToModuleMap = projectToModule.toMap
    createModuleDependencies(projectToModuleMap)

    projectToModuleMap
  }

  private def createLibraries(data: sbtStructure.StructureData, projects: Seq[sbtStructure.ProjectData]): Seq[LibraryNode] = {
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
    val ProjectData(_, _, _, _, _, _, packagePrefix, basePackages, _, _, java, scala, android, _, _, _, _, _, _) = project

    val sdk = android.map(_.targetVersion).map(AndroidJdk)
      .orElse(java.flatMap(_.home).map(JdkByHome))

    val data = ModuleExtData(
      scala.map(_.version),
      scala.fold(Seq.empty[File])(_.jars),
      scala.fold(Seq.empty[String])(_.options),
      sdk,
      java.fold(Seq.empty[String])(_.options),
      packagePrefix,
      basePackages.headOption // TODO Rename basePackages to basePackage in sbt-ide-settings?
    )
    new ModuleExtNode(data)
  }


  private def createTaskData(project: sbtStructure.ProjectData): Seq[SbtTaskNode] = {
    project.tasks.map { t =>
      new SbtTaskNode(SbtTaskData(t.label, t.description.getOrElse(""), t.rank))
    }
  }

  private def createSettingData(project: sbtStructure.ProjectData): Seq[SbtSettingNode] = {
    project.settings.map { s =>
      // TODO use options for description, value and handle them in the UI appropriately
      new SbtSettingNode(SbtSettingData(s.label, s.description.getOrElse(""), s.rank, s.stringValue.getOrElse("")))
    }
  }

  private def createCommandData(project: sbtStructure.ProjectData) = {
     project.commands.map { c =>
      new SbtCommandNode(SbtCommandData(c.name, c.help))
    }
  }

  private def createFacet(project: sbtStructure.ProjectData, android: sbtStructure.AndroidData): AndroidFacetNode = {
    new AndroidFacetNode(AndroidFacetData(android.targetVersion, android.manifest, android.apk,
                         android.res, android.assets, android.gen, android.libs,
                         android.isLibrary, android.proguardConfig))
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
    result.addPaths(LibraryPathType.SOURCE, module.sources.map(_.path).toSeq)
    result.addPaths(LibraryPathType.DOC, module.docs.map(_.path).toSeq)
    result
  }

  private def nameFor(id: sbtStructure.ModuleIdentifier) = {
    if (IJ_SDK_CLASSIFIERS.contains(id.classifier)) { // DevKit expects IJ SDK library names in certain format for some features to work
      s"[${id.classifier}]${id.organization}:${id.name}:${id.revision}"
    } else {
      val classifierOption = if (id.classifier.isEmpty) None else Some(id.classifier)
      s"${id.organization}:${id.name}:${id.revision}" + classifierOption.map(":" + _).getOrElse("") + s":${id.artifactType}"
    }
  }

  private def createModule(project: sbtStructure.ProjectData, moduleFilesDirectory: File, moduleName: String): ModuleNode = {
    // TODO use both ID and Name when related flaws in the External System will be fixed
    // TODO explicit canonical path is needed until IDEA-126011 is fixed
    val projectId = ModuleNode.combinedId(project.id, Option(project.buildURI))
    val result = new ModuleNode(StdModuleTypes.JAVA.getId, projectId, project.buildURI, moduleName,
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

    result.storePaths(ExternalSystemSourceType.SOURCE, unmanagedDirectories(productionSources))
    result.storePaths(ExternalSystemSourceType.SOURCE_GENERATED, managedDirectories(productionSources))
    result.storePaths(ExternalSystemSourceType.RESOURCE, allDirectories(productionResources))

    result.storePaths(ExternalSystemSourceType.TEST, unmanagedDirectories(testSources))
    result.storePaths(ExternalSystemSourceType.TEST_GENERATED, managedDirectories(testSources))
    result.storePaths(ExternalSystemSourceType.TEST_RESOURCE, allDirectories(testResources))

    getExcludedTargetDirs(project).foreach { path =>
      result.storePath(ExternalSystemSourceType.EXCLUDED, path.path)
    }

    result
  }

  private def allDirectories(dirs: Seq[sbtStructure.DirectoryData]) =
    dirs.map(_.file.canonicalPath)

  private def managedDirectories(dirs: Seq[sbtStructure.DirectoryData]) =
    dirs.filter(_.managed).map(_.file.canonicalPath)

  private def unmanagedDirectories(dirs: Seq[sbtStructure.DirectoryData]) =
    dirs.filterNot(_.managed).map(_.file.canonicalPath)

  // We cannot always exclude the whole ./target/ directory because of
  // the generated sources, so we resort to an heuristic.
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

  private def createBuildModule(build: sbtStructure.BuildData, projects: Seq[ProjectData], moduleFilesDirectory: File, localCachePath: Option[String]): ModuleNode = {

    val buildBaseProject =
      projects
        .filter(p => p.buildURI == build.uri)
        .foldLeft(None: Option[ProjectData]) {
          case (None, p) => Some(p)
          case (Some(p), p1) =>
            val parent = if (p.base.isAncestorOf(p1.base)) p else p1
            Some(parent)
        }

    val buildId = buildBaseProject
      .map(_.name +  Sbt.BuildModuleSuffix)
      .getOrElse(build.uri.toString)

    val buildBaseDir = buildBaseProject
      .map(_.base)
      .getOrElse {
        if (build.uri.getScheme == "file") new File(build.uri.getPath)
        else projects.head.base // this really shouldn't happen
      }

    val buildRoot = buildBaseDir / Sbt.ProjectDirectory

    // TODO explicit canonical path is needed until IDEA-126011 is fixed
    val result = new ModuleNode(SbtModuleType.instance.getId, buildId, build.uri, buildId, moduleFilesDirectory.path, buildRoot.canonicalPath)

    //todo: probably it should depend on sbt version?
    result.add(ModuleSdkNode.inheritFromProject)

    result.setInheritProjectCompileOutputPath(false)
    result.setCompileOutputPath(ExternalSystemSourceType.SOURCE, (buildRoot / Sbt.TargetDirectory / "idea-classes").path)
    result.setCompileOutputPath(ExternalSystemSourceType.TEST, (buildRoot / Sbt.TargetDirectory / "idea-test-classes").path)
    result.add(createBuildContentRoot(buildRoot))

    val library = {
      val classes = build.classes.filter(_.exists).map(_.path)
      val docs = build.docs.filter(_.exists).map(_.path)
      val sources = build.sources.filter(_.exists).map(_.path)
      createModuleLevelDependency(Sbt.BuildLibraryName, classes, docs, sources, DependencyScope.COMPILE)(result)
    }

    result.add(library)

    result.add(createSbtBuildModuleData(build, projects, localCachePath))

    result
  }

  private def createBuildContentRoot(buildRoot: File): ContentRootNode = {
    val result = new ContentRootNode(buildRoot.path)

    val sourceDirs = Seq(buildRoot) // , base << 1

    val excludedDirs = Seq(
      buildRoot / Sbt.TargetDirectory,
      buildRoot / Sbt.ProjectDirectory / Sbt.TargetDirectory)

    result.storePaths(ExternalSystemSourceType.SOURCE, sourceDirs.map(_.path))
    result.storePaths(ExternalSystemSourceType.EXCLUDED, excludedDirs.map(_.path))

    result
  }

  private def createSbtBuildModuleData(build: sbtStructure.BuildData, projects: Seq[ProjectData], localCachePath: Option[String]): SbtBuildModuleNode = {

    val buildProjects = projects.filter(p => p.buildURI == build.uri)
    val imports = build.imports.flatMap(_.trim.substring(7).split(", "))
    val projectResolvers = buildProjects.flatMap(_.resolvers)
    val resolvers = projectResolvers.map { r => new SbtMavenResolver(r.name, r.root).asInstanceOf[SbtResolver] }

    new SbtBuildModuleNode(SbtBuildModuleData(imports, resolvers.toSet + SbtResolver.localCacheResolver(localCachePath), build.uri))
  }

  private def validRootPathsIn(project: sbtStructure.ProjectData, scope: String)
                              (selector: sbtStructure.ConfigurationData => Seq[sbtStructure.DirectoryData]): Seq[sbtStructure.DirectoryData] = {
    project.configurations
            .find(_.id == scope)
            .map(selector)
            .getOrElse(Seq.empty)
            .filterNot(_.file.isOutsideOf(project.base))
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

  override def cancelTask(taskId: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener): Boolean =
    //noinspection UnitInMap
    activeProcessDumper
      .map(_.cancel())
      .isDefined

}

object SbtProjectResolver {

  val IJ_SDK_CLASSIFIERS = Set("IJ-SDK", "IJ-PLUGIN")

  case class ImportCancelledException(cause: Throwable) extends Exception(cause)

  val SBT_PROCESS_CHECK_TIMEOUT_MSEC = 100

  def shellImportSupported(sbtVersion: Version): Boolean =
    sbtVersion >= sinceSbtVersionShell

  def importSupported(sbtVersion: Version): Boolean =
    sbtVersion >= sinceSbtVersion

  // TODO shared code, move to a more suitable object
  val sinceSbtVersion: Version = Version("0.13.0")

  // TODO shared code, move to a more suitable object
  val sinceSbtVersionShell: Version = Version("0.13.5")

}
