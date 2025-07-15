package org.jetbrains.sbt.project

import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.project.{ProjectData => ESProjectData, _}
import com.intellij.openapi.externalSystem.model.task.event.{Failure => ESFailure, _}
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.model.{DataNode, ExternalSystemException}
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.util.SystemProperties
import org.jetbrains.annotations.{ApiStatus, NonNls, Nullable, TestOnly}
import org.jetbrains.plugins.scala._
import org.jetbrains.plugins.scala.build._
import org.jetbrains.plugins.scala.compiler.data.CompileOrder
import org.jetbrains.plugins.scala.extensions.{PathExt, RichFile}
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.plugins.scala.project.external.{JdkByHome, JdkByName, SdkReference}
import org.jetbrains.plugins.scala.util.ScalaNotificationGroups
import org.jetbrains.sbt.SbtUtil._
import org.jetbrains.sbt.project.SbtProjectResolver._
import org.jetbrains.sbt.project.SourceSetType.SourceSetType
import org.jetbrains.sbt.project.data._
import org.jetbrains.sbt.project.module.SbtModuleType
import org.jetbrains.sbt.project.settings._
import org.jetbrains.sbt.project.structure.SbtStructureDump.PrintProcessOutputOnFailurePropertyName
import org.jetbrains.sbt.project.structure._
import org.jetbrains.sbt.resolvers.{SbtIvyResolver, SbtMavenResolver, SbtResolver}
import org.jetbrains.sbt.structure.XmlSerializer._
import org.jetbrains.sbt.structure.{BuildData, CompilerOptions, Configuration, ConfigurationData, Dependencies, DependencyData, DirectoryData, JarDependencyData, JavaData, ModuleDependencyData, ModuleIdentifier, ProjectData, ProjectDependencyData, ScalaData}
import org.jetbrains.sbt.{RichBoolean, Sbt, SbtBundle, SbtUtil, SbtVersion, usingTempFile, structure => sbtStructure}

import java.io.{File, FileNotFoundException}
import java.net.URI
import java.nio.file.Path
import java.util.{Collections, Locale, UUID}
import scala.collection.{MapView, mutable}
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Random, Success, Try}
import scala.xml.{Elem, XML}

/**
 * @see [[com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver]]
 * @see [[com.intellij.openapi.externalSystem.service.remote.wrapper.ExternalSystemProjectResolverWrapper]]
 */
class SbtProjectResolver extends ExternalSystemProjectResolver[SbtExecutionSettings] with ExternalSourceRootResolution with ContentRootsResolution {

  private val log = Logger.getInstance(getClass)

  @volatile private var activeProcessDumper: Option[SbtStructureDump] = None

  override def resolveProjectInfo(
    taskId: ExternalSystemTaskId,
    wrongProjectPathDontUseIt: String,
    isPreview: Boolean,
    settings: SbtExecutionSettings,
    listener: ExternalSystemTaskNotificationListener
  ): DataNode[ESProjectData] = {
    val projectRoot = {
      val file = new File(settings.realProjectPath)
      if (file.isDirectory) file else file.getParentFile
    }

    val sbtLauncher = SbtUtil.getLauncherJar(settings)

    implicit val context: ImportContext = ImportContext(settings)

    if (isPreview) dummyProject(projectRoot, settings).toDataNode
    else importProject(taskId, settings, projectRoot, sbtLauncher.toFile, listener)
  }

  private def importProject(
    taskId: ExternalSystemTaskId,
    settings: SbtExecutionSettings,
    projectRoot: File,
    sbtLauncher: File,
    notifications: ExternalSystemTaskNotificationListener
  )(implicit context: ImportContext): DataNode[ESProjectData] = {

    @NonNls val importTaskId = s"import:${UUID.randomUUID()}"
    val importTaskDescriptor =
      new TaskOperationDescriptor(SbtBundle.message("sbt.import.to.intellij.project.model"), System.currentTimeMillis(), "project-model-import")

    val esReporter = new ExternalSystemNotificationReporter(projectRoot.getAbsolutePath, taskId, notifications)
    implicit val reporter: BuildReporter = if (isUnitTestMode) {
      val logReporter = new LogReporter
      new CompositeReporter(esReporter, logReporter)
    } else esReporter

    @Nullable val ideaProject: Project = taskId.findProject()

    val startTime = System.currentTimeMillis()
    val structureDump = dumpStructure(projectRoot, sbtLauncher, context.sbtVersion, settings, ideaProject)

    // side-effecty status reporting
    structureDump.foreach { _ =>
      val convertStartEvent = new ExternalSystemStartEvent(importTaskId, null, importTaskDescriptor)
      val event = new ExternalSystemTaskExecutionEvent(taskId, convertStartEvent)
      notifications.onStatusChange(event)
    }

    val conversionResult: Try[DataNode[ESProjectData]] = structureDump
      .map { case (elem, _) =>
        val data = elem.deserialize[sbtStructure.StructureData].getOrElse(throw new IllegalStateException("Could not deserialize sbt structure data"))
        convert(normalizePath(projectRoot), data, settings.jdk, settings, Option(ideaProject)).toDataNode
      }
      .recoverWith {
        case ImportCancelledException(cause) =>
          val causeMessage = if (cause != null) cause.getMessage else SbtBundle.message("sbt.unknown.cause")

          // notify user if project exists already
          val projectOpt = ProjectManager.getInstance().getOpenProjects.find(p => FileUtil.pathsEqual(p.getBasePath, projectRoot.getCanonicalPath))
          projectOpt.foreach { p =>
            val notification = ScalaNotificationGroups.sbtProjectImport.createNotification(SbtBundle.message("sbt.import.cancelled", causeMessage), NotificationType.INFORMATION)
            notification.notify(p)
          }

          log.info("sbt import cancelled", cause)
          // sorry, ExternalSystem expects a null when resolving is not possible
          Success(null)
        case x: Exception =>
          Failure(new ExternalSystemException(x))
      }

    // more side-effecty reporting
    val endTime = System.currentTimeMillis()
    val resultNode = conversionResult match {
      case Success(_) =>
        new SuccessResult(startTime, endTime, true)
      case Failure(_) =>
        new FailureResult(startTime, endTime, Collections.emptyList[ESFailure]) // TODO error list
    }
    val convertFinishedEvent = new ExternalSystemFinishEvent[TaskOperationDescriptor](
      importTaskId, null, importTaskDescriptor, resultNode
    )
    val event = new ExternalSystemTaskExecutionEvent(taskId, convertFinishedEvent)
    notifications.onStatusChange(event)

    conversionResult.get // ok to throw here, that's the way ExternalSystem likes it
  }

  private def dumpStructure(
    projectRoot: File,
    sbtLauncher: File,
    sbtVersion: SbtVersion,
    settings: SbtExecutionSettings,
    @Nullable project: Project
  )(implicit reporter: BuildReporter): Try[(Elem, BuildMessages)] = {
    SbtProjectResolver.processOutputOfLatestStructureDump = ""

    val useShellImport = settings.useShellForImport && project != null
    val options = getSbtStructureDumpOptions(settings)

    def doDumpStructure(structureFile: File): Try[(Elem, BuildMessages)] = {
      val structureFilePath = normalizePath(structureFile)

      val dumper = new SbtStructureDump()
      activeProcessDumper = Option(dumper)

      val messageResult: Try[BuildMessages] = {
        if (useShellImport) {
          val messagesF = dumper.dumpFromShell(
            project,
            sbtVersion,
            structureFilePath,
            options,
            reporter,
            settings.preferScala2,
            settings.generateManagedSourcesDuringProjectSync
          )
          Try(Await.result(messagesF, Duration.Inf)) // TODO some kind of timeout / cancel mechanism
        }
        else {
          val sbtStructureJar = settings
            .customSbtStructureFile
            .orElse(SbtUtil.getSbtStructureJar(sbtVersion))
            .getOrElse(throw new ExternalSystemException(s"Could not find sbt-structure-extractor for sbt version $sbtVersion"))

          log.debug(s"sbtStructureJar: $sbtStructureJar")
          // TODO add error/warning messages during dump, report directly
          dumper.dumpFromProcess(
            projectRoot,
            structureFilePath,
            options,
            settings.vmExecutable,
            settings.vmOptions,
            settings.sbtOptions,
            settings.userSetEnvironment,
            sbtLauncher,
            sbtStructureJar,
            settings.preferScala2,
            settings.passParentEnvironment,
            settings.generateManagedSourcesDuringProjectSync
          )
        }
      }
      activeProcessDumper = None

      val result: Try[(Elem, BuildMessages)] = messageResult.flatMap { messages =>
        val tried = {
          def failure(reason: String): Failure[(Elem, BuildMessages)] = {
            val message = SbtBundle.message("sbt.import.extracting.structure.failed") + s": $reason"
            Failure(new Exception(message))
          }

          if (messages.status != BuildMessages.OK)
            failure(SbtBundle.message("sbt.import.message.build.status", messages.status))
          else if (!structureFile.isFile)
            failure(SbtBundle.message("sbt.import.message.structure.file.is.not.a.file", structureFile.getPath))
          else if (structureFile.length <= 0)
            failure(SbtBundle.message("sbt.import.message.structure.file.is.empty", structureFile.getPath))
          else Try {
            val elem = XML.load(structureFile.toURI.toURL)
            (elem, messages)
          }
        }

        tried.recoverWith { case error =>
          val exceptionsText = messages.exceptions.map(_.getLocalizedMessage).mkString("\n")
          val errorsText = messages.errors.map(_.getMessage).mkString("\n")
          val message = error.getMessage + "\n" +
            exceptionsText + (if (exceptionsText.nonEmpty) "\n" else "") +
            errorsText + (if (errorsText.nonEmpty) "\n" else "") +
            //add process output to the exception to easily see the error in tests, without need to dig the logs
            (if (isUnitTestMode) "Process output:\n" + dumper.processOutput else "")
          Failure(new Exception(message.stripTrailing, error.getCause))
        }
      }

      lazy val processOutput = dumper.processOutput.mkString
      if (isUnitTestMode) {
        SbtProjectResolver.processOutputOfLatestStructureDump = processOutput
      }
      if (result.isFailure) {
        //NOTE: exception is logged in other places
        val processOutputHint =
          if (processOutput.nonEmpty) s", sbt process output:\n$processOutput"
          else s" (to see sbt process output pass -D$PrintProcessOutputOnFailurePropertyName=true)"
        log.debug(s"""failed to dump sbt structure $processOutputHint""")
      }
      result
    }

    if (!sbtLauncher.isFile) {
      val error = SbtBundle.message("sbt.launcher.not.found", sbtLauncher.getCanonicalPath)
      Failure(new FileNotFoundException(error))
    } else {
      if (sbtVersion.isSbt0) {
        LegacySbtVersionNotifications.warnForBuildToolWindow(project, projectRoot, sbtVersion, reporter)
      }

      val structureFilePath = getStructureFilePath(projectRoot)
      val StructureFileReuseMode(readStructureFile, writeStructureFile) = getStructureFileReuseMode

      if (readStructureFile && structureFilePath.exists()) {
        val reuseWarning = s"sbt reload skipped: using existing structure file: $structureFilePath"
        log.warn(reuseWarning)
        //noinspection ReferencePassedToNls (this branch is only triggered when registry was explicitly modified, so it's not i18-ed)
        reporter.log(reuseWarning)
        val elem = XML.load(structureFilePath.toURI.toURL)
        Try((elem, BuildMessages.empty))
      } else if (writeStructureFile) {
        log.warn(s"reused structure file created: $structureFilePath")
        doDumpStructure(structureFilePath)
      } else {
        usingTempFile("sbt-structure", Some(".xml")) { structureFile =>
          doDumpStructure(structureFile.toFile)
        }
      }
    }
  }

  private def getStructureFilePath(projectRoot: File): File = {
    var structureFileFolder = new File(Option(System.getProperty("sbt.project.structure.location")).getOrElse(FileUtil.getTempDirectory))
    if (!structureFileFolder.isAbsolute) {
      structureFileFolder = projectRoot.toPath.resolve(structureFileFolder.toPath).normalize().toFile
    }
    structureFileFolder / s"sbt-structure-reused-${projectRoot.getName}.xml"
  }

  //noinspection NameBooleanParameters
  private def getStructureFileReuseMode: StructureFileReuseMode =
    if (RegistryManager.getInstance().is("sbt.project.import.reuse.previous.structure.file"))
      StructureFileReuseMode(true, true)
    else if (java.lang.Boolean.parseBoolean(System.getProperty("sbt.project.structure.readWrite")))
      StructureFileReuseMode(true, true)
    else if (java.lang.Boolean.parseBoolean(System.getProperty("sbt.project.structure.write")))
      StructureFileReuseMode(false, true)
    else if (java.lang.Boolean.parseBoolean(System.getProperty("sbt.project.structure.read")))
      StructureFileReuseMode(true, false)
    else
      StructureFileReuseMode(false, false)

  private case class StructureFileReuseMode(
    readStructureFile: Boolean,
    writeStructureFile: Boolean
  )

  private def getSbtStructureDumpOptions(settings: SbtExecutionSettings): Seq[String] =
    Seq("download") ++
      settings.resolveClassifiers.seq("resolveSourceClassifiers") ++
      settings.resolveSbtClassifiers.seq("resolveSbtClassifiers") ++
      settings.separateProdTestSources.seq("separateProdAndTestSources")


  /**
   * Create project preview without using sbt, since sbt import can fail and users would have to do a manual edit of the project.
   * Also sbt boot makes the whole process way too slow.
   */
  private def dummyProject(
    projectRoot: File,
    settings: SbtExecutionSettings,
  )(implicit context: ImportContext): Node[ESProjectData] = {

    // TODO add default scala sdk and sbt libs (newest versions or so)

    val projectUri = projectRoot.toURI
    val projectPath = projectRoot.getAbsolutePath
    val projectName = normalizeModuleId(projectRoot.getName)
    val projectTmpName = projectName + "_" + Random.nextInt(10000)
    val sourceDir = new File(projectRoot, "src/main/scala")
    val classDir = new File(projectRoot, "target/dummy")

    val dummyConfigurationData = ConfigurationData(CompileScope, Seq(DirectoryData(sourceDir, managed = false)), Seq.empty, Seq.empty, classDir)
    val dummyJavaData = JavaData(None, Seq.empty)
    val dummyDependencyData = DependencyData(Dependencies(Seq.empty, Seq.empty), Dependencies(Seq.empty, Seq.empty), Dependencies(Seq.empty, Seq.empty))
    val dummyRootProject = ProjectData(
      projectTmpName, projectUri, projectTmpName, s"org.$projectName", "0.0", projectRoot, None, Seq.empty,
      new File(projectRoot, "target"), Seq(dummyConfigurationData), Option(dummyJavaData), None, CompileOrder.Mixed.toString,
      dummyDependencyData, Set.empty, None, Seq.empty, Seq.empty, Seq.empty, Seq(), Seq(), generatedManagedSources = false
    )

    val projects = Seq(dummyRootProject)

    val projectNode = new ProjectNode(projectName, projectPath, projectPath)
    val libraryNodes = Seq.empty[LibraryNode]
    val buildProjectsGroup = Seq(BuildProjectsGroup(projectUri, dummyRootProject, Nil, projectTmpName))
    val projectToModule = createIntelliJModuleNodes(
      buildProjectsGroup,
      groupedSharedRoots = Nil,
      libraryNodes,
      projectRoot,
    )

    val dummySbtProjectData = SbtProjectData(
      settings.jdk.map(JdkByName),
      context.sbtVersion.minor,
      projectPath,
      prodTestSourcesSeparated = false,
      isPreview = true
    )
    projectNode.add(new SbtProjectNode(dummySbtProjectData))
    val modules = projectToModule.values.map(_.parent)
    projectNode.addAll(modules)

    val dummyBuildData = BuildData(projectUri, Seq.empty, Seq.empty, Seq.empty, Seq.empty)
    val projectToParentModule = projectToModule.view.mapValues(_.parent).toMap
    createBuildModule(
      dummyBuildData,
      projects,
      getDefaultModuleFilesDirectory(projectRoot),
      None,
      projectToParentModule,
      buildProjectsGroup
    )

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

  private def notifyGeneratedManagedSources(projectPath: String, data: sbtStructure.StructureData, optIdeaProject: Option[Project]): Unit = {
    optIdeaProject.foreach { ideaProject =>
      val generatedManagedSources = data.projects.exists(_.generatedManagedSources)
      if (!ideaProject.isDisposed) {
        GeneratedManagedSourcesService.instance(ideaProject).setGeneratedForPath(projectPath, generatedManagedSources)
      }
    }
  }

  private def convert(
    root: String,
    data: sbtStructure.StructureData,
    settingsJdk: Option[String],
    settings: SbtExecutionSettings,
    optIdeaProject: Option[Project]
  )(implicit context: ImportContext): Node[ESProjectData] = {
    val projects: Seq[sbtStructure.ProjectData] = data.projects
    val projectRootFile = new File(root)
    val rootProject: sbtStructure.ProjectData =
      projects.find(p => FileUtil.filesEqual(p.base, projectRootFile))
        .orElse(projects.headOption)
        .getOrElse(throw new RuntimeException("No root project found"))
    val projectNode = new ProjectNode(rootProject.name, root, root)

    val projectJdk = chooseJdk(rootProject, settingsJdk)

    projectNode.add(
      new SbtProjectNode(
        SbtProjectData(
          projectJdk,
          data.sbtVersion,
          root,
          settings.separateProdTestSources
        )
      )
    )

    val newPlay2Data = projects.flatMap(p => p.play2.map(d => (p.id, p.base, d)))
    projectNode.add(new Play2ProjectNode(Play2OldStructureAdapter(newPlay2Data)))

    val projectLibraryNodes = createLibraries(data, projects)
    projectNode.addAll(projectLibraryNodes)

    val groupedSharedRoots = groupSharedRoots(projects, projectRootFile)

    val buildProjectsGroups: Seq[BuildProjectsGroup] = createBuildProjectGroups(projects)
    val projectToModule: Map[ProjectData, ModuleSourceSet] = createIntelliJModuleNodes(
      buildProjectsGroups,
      groupedSharedRoots,
      projectLibraryNodes,
      projectRootFile,
    )

    //Sort modules by id to make project imports more reproducible
    //In particular, this will easy testing of `org.jetbrains.sbt.project.SbtProjectImportingTest.testSCL13600`
    //(note, still the order can be different on different machine, because id depends on URI)
    val projectToParentModule = projectToModule.view.mapValues(_.parent).toMap
    val modulesSorted: Seq[ModuleDataNodeType] = projectToParentModule.values.toSeq.sortBy(_.getId)
    projectNode.addAll(removeNestedModuleNodes(modulesSorted))

    val defaultModuleFilesDirectory = getDefaultModuleFilesDirectory(projectRootFile)
    addSharedSourceModules(
      groupedSharedRoots,
      projectToModule,
      projectLibraryNodes,
      defaultModuleFilesDirectory,
      settings.separateProdTestSources,
      buildProjectsGroups
    )

    val buildModuleForProject: BuildData => BuildModuleNodeWithBuildBaseDir =
      build => createBuildModule(
        build,
        projects,
        defaultModuleFilesDirectory,
        data.localCachePath.map(_.getCanonicalPath),
        projectToParentModule,
        buildProjectsGroups
      )
    val buildModules = data.builds.map(buildModuleForProject)

    configureBuildModuleDependencies(buildModules)

    notifyGeneratedManagedSources(root, data, optIdeaProject)

    projectNode
  }

  private def removeNestedModuleNodes(nodes: Seq[ModuleDataNodeType]): Seq[ModuleDataNodeType] =
    nodes.filterNot(_.isInstanceOf[NestedModuleNode])

  private def findRootNodeForBuild(
    buildProjectsGroups: Seq[BuildProjectsGroup],
    buildModuleBaseDir: File,
    projectToModule: Map[ProjectData, ModuleDataNodeType]
  ): Option[ModuleDataNodeType] = {
    val rootProjectOpt = buildProjectsGroups
      .find(_.rootProject.base == buildModuleBaseDir)
      .map(_.rootProject)
    val rootProjectModuleName = rootProjectOpt
      .flatMap(projectToModule.get)
    rootProjectModuleName
  }

  /**
   * Some SBT builds can have nested sbT builds.
   * Scala Plugin project is a good example for that.
   * There is Ultimate part and Community part and Community part is a nested build for Ultimate.
   * In order we can resolve entities of community module in ultimate module
   * we need to add a dependency on `scalaCommunity-build` module to `scalaUltimate-build` module.
   *
   * @todo So far this is a hacky solution which only works for 2s build modules.
   *       It's primarily designed to work in Scala Plugin project.
   *       It doesnt work in case there are more nested projects.
   *       For that case a more general solution is needed, but it would be nice to have more project examples
   *
   * @todo Actually, looks like this workaround is not correct.<br>
   *       By default, definitions in nested project can't be accessed from containing project.
   *       The reason why in `ultimateRoot/build.sbt` we can see definitions from `ultimateRoot/community/proejct`
   *       is because we explicitly add unmanaged sources in `ultimateRoot/project/build.sbt`: {{{
   *         Compile / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "community" / "project"
   *       }}}<br>
   *       See also https://youtrack.jetbrains.com/issue/SCL-13573/Apply-shared-external-source-directory-logic-for-sbt-build-modules
   */
  private def configureBuildModuleDependencies(buildModules: Seq[BuildModuleNodeWithBuildBaseDir]): Unit = {
    if (buildModules.size == 2) {
      val Seq(module1, module2) = buildModules
      if (isChild(module1.buildBaseDir.toPath, module2.buildBaseDir.toPath)) {
        addModuleDependencyNode(module2.moduleNode, module1.moduleNode, DependencyScope.COMPILE)
      }
      else if (isChild(module2.buildBaseDir.toPath, module1.buildBaseDir.toPath)) {
        addModuleDependencyNode(module1.moduleNode, module2.moduleNode, DependencyScope.COMPILE)
      }
      else {
        //modules are not hierarchical? Not sure if such case possible but will leave the empty branch here
      }
    }
  }

  private def isChild(child: Path, parentPath: Path): Boolean = {
    val parent = parentPath.normalize()
    child.normalize().startsWith(parent)
  }

  /** Choose a project jdk based on information from sbt settings and IDE.
   * More specific settings from sbt are preferred over IDE settings, on the assumption that the sbt project definition
   * is what is more likely to be under source control.
   */
  private def chooseJdk(project: sbtStructure.ProjectData, defaultJdk: Option[String]): Option[SdkReference] = {
    // TODO put some of this logic elsewhere in resolving process?
    val jdkHomeInSbtProject = project.java.flatMap(_.home).map(home => JdkByHome(home.toPath))

    // default either from project structure or initial import settings
    val default = defaultJdk.map(JdkByName)

    jdkHomeInSbtProject
      .orElse(default)
  }

  private def mapToModuleNodeToDependencies(projectToSourceSet: Map[ProjectData, ModuleSourceSet]): Map[ModuleDataNodeType, Seq[ProjectDependencyData]] =
    projectToSourceSet.flatMap {
      case (projectData, PrentModuleSourceSet(parent)) =>
        Seq((parent, projectData.dependencies.projects.forProduction))
      case (projectData, CompleteModuleSourceSet(_, main, test)) =>
        val projectDependencies = projectData.dependencies.projects
        Seq((main, projectDependencies.forProduction), (test, projectDependencies.forTest))
    }

  private def addAllModuleDependencies(projectToModule: Map[ProjectData, ModuleSourceSet], useSeparateMainTestModules: Boolean): Unit = {
    val moduleToDependencies = mapToModuleNodeToDependencies(projectToModule)
    val allSourceSetModules = collectSourceModules(projectToModule)
    moduleToDependencies.foreach { case (module, deps) =>
      addModuleDependencies(deps, allSourceSetModules, module, useSeparateMainTestModules)
    }
  }

  private def createIntelliJModuleNodes(
    projectsGrouped: Seq[BuildProjectsGroup],
    groupedSharedRoots: Seq[SharedSourcesGroup],
    projectLibraryNodes: Seq[LibraryNode],
    projectRoot: File,
  )(implicit context: ImportContext): Map[ProjectData, ModuleSourceSet] = {
    val librariesData = projectLibraryNodes.map(_.data)

    val projectsSourcesDetails =
      if (context.useSeparateProdTestSources) resolveProjectsSourcesDetails(projectsGrouped, groupedSharedRoots)
      else Map.empty[ProjectData, ProjectSourcesDetails]

    val projectToModule: Iterable[(ProjectData, ModuleSourceSet)] = projectsGrouped.flatMap { buildProjectsGroup =>
      createModulesInsideBuildProjectGroup(
        buildProjectsGroup,
        projectRoot,
        librariesData,
        projectsSourcesDetails
      )
    }

    val projectToModuleMap = projectToModule.toMap
    addAllModuleDependencies(projectToModuleMap, context.useSeparateProdTestSources)

    projectToModuleMap
  }

  /**
   * In this method the grouping of modules is done in such a way that projects that belonging to the same build are grouped together (when there are at least 2 builds).
   * Additionally, the root node (displayed in <code>Project Structure | Modules</code>) for projects inside single build is the root project of this build.
   * Because of that, the root project in each build does not participate in the grouping of modules.
   */
  private def createModulesInsideBuildProjectGroup(
    buildProjectsGroup: BuildProjectsGroup,
    projectRoot: File,
    librariesData: Seq[LibraryData],
    projectsSourcesDetails: Map[ProjectData, ProjectSourcesDetails]
  )(implicit context: ImportContext): Seq[(ProjectData, ModuleSourceSet)] = {
    val BuildProjectsGroup(_, rootProject, projects, rootProjectModuleNameUnique) = buildProjectsGroup


    def createModule(
      project: sbtStructure.ProjectData,
      moduleName: String,
      moduleGroup: Option[String],
      shouldCreateNestedModule: Boolean,
    ): ModuleSourceSet =
      if (context.useSeparateProdTestSources) {
        val projectSourcesDetails = projectsSourcesDetails.getOrElse(project, ProjectSourcesDetails.default)
        createModuleWithAllRequiredDataForSeparateProdAndTestSources(
          project, projectRoot, moduleName, moduleGroup, librariesData, shouldCreateNestedModule, projectSourcesDetails
        )
      } else {
        createModuleWithAllRequiredDataLegacy(
          project, projectRoot, moduleName, moduleGroup, librariesData, shouldCreateNestedModule
        )
      }

    val rootModuleSourceSet = createModule(
      project = rootProject,
      moduleName = rootProjectModuleNameUnique,
      moduleGroup = None,
      shouldCreateNestedModule = false
    )

    val parentModule = rootModuleSourceSet.parent
    val projectNameToProject = projects.groupBy(_.name)
    val projectToModuleForNonRootProjects = projects.map { project =>
      val (moduleName, moduleGroup) = generateModuleAndGroupName(project, parentModule.getInternalName, projectNameToProject)
      val moduleSourceSet = createModule(
        project = project,
        moduleName = moduleName,
        moduleGroup = Some(moduleGroup),
        shouldCreateNestedModule = true,
      )
      parentModule.add(moduleSourceSet.parent)
      (project, moduleSourceSet)
    }
    projectToModuleForNonRootProjects :+ (rootProject, rootModuleSourceSet)
  }

  private def generateModuleAndGroupName(
    projectData: ProjectData,
    rootProjectInternalName: String,
    projectNameToProject: Map[String, Seq[ProjectData]],
  ): (String, String) = {
    val projectName = projectData.name
    val projectsWithSameNameInBuild: Seq[ProjectData] = projectNameToProject.get(projectName).toSeq.flatten

    val nameIsUnique = projectsWithSameNameInBuild.size == 1
    val moduleName =
      if (nameIsUnique) projectName
      else projectData.id

    val groupNameInsideBuild = if (projectsWithSameNameInBuild.size > 1) Seq(projectName) else Nil
    val moduleGroups = (rootProjectInternalName +: groupNameInsideBuild).mkString(".")
    (moduleName, moduleGroups)
  }

  private def createBuildProjectGroups(projects: Seq[ProjectData]): Seq[BuildProjectsGroup] = {
    val buildToProjects: Map[URI, Seq[ProjectData]] =
      projects.groupBy(_.buildURI)

    //NOTE: sort by URI for a better reproducibility/testability of the resulting project structure
    //The matters for unique group names generation
    //(if the order is not specified, group names of projects with colliding names can have random index suffixes)
    buildToProjects
      .toSeq.sortBy(_._1)
      .map { case (buildUri, projects) =>
        val rootProject = findRootProjectInBuild(projects)
        val projectsWithoutRootProject = projects.filterNot(_ == rootProject)
        BuildProjectsGroup(buildUri, rootProject, projectsWithoutRootProject, rootProject.name)
      }
  }

  private def findRootProjectInBuild(projectInSameBuild: Seq[ProjectData]): ProjectData = {
    //Assuming that all projects in same build are located in the same directory
    //I checked with SBT 1.9.6 and if you try to define a module outside current build root it throws an error:
    // `java.lang.AssertionError: assertion failed: directory ... is not contained in build root`
    projectInSameBuild.minBy(_.base.getPath.length)
  }

  private def createLibraries(data: sbtStructure.StructureData, projects: Seq[sbtStructure.ProjectData]): Seq[LibraryNode] = {
    val repositoryModules = data.repository.map(_.modules).getOrElse(Seq.empty)
    val (modulesWithoutBinaries, modulesWithBinaries) = repositoryModules.partition(_.binaries.isEmpty)
    val otherModuleIds = projects.flatMap { proj =>
      val dependencies = proj.dependencies.modules
      val prodAndTest = dependencies.forProduction ++ dependencies.forTest
      prodAndTest.map(_.id)
    }.diff(repositoryModules.map(_.id))

    val libs = modulesWithBinaries.map(createResolvedLibrary) ++ otherModuleIds.map(createUnresolvedLibrary)

    val modulesWithDocumentation = modulesWithoutBinaries.filter(m => m.docs.nonEmpty || m.sources.nonEmpty)
    if (modulesWithDocumentation.isEmpty) return libs

    val unmanagedSourceLibrary = new LibraryNode(Sbt.UnmanagedSourcesAndDocsName, true)
    unmanagedSourceLibrary.addPaths(LibraryPathType.DOC, modulesWithDocumentation.flatMap(_.docs).map(_.path))
    unmanagedSourceLibrary.addPaths(LibraryPathType.SOURCE, modulesWithDocumentation.flatMap(_.sources).map(_.path))
    libs :+ unmanagedSourceLibrary
  }

  protected def createScalaSdkData(scala: Option[ScalaData]): ScalaSdkNode = {
    val data = SbtScalaSdkData(
      scalaVersion = scala.map(_.version),
      scalacClasspath = scala.fold(Seq.empty[File])(_.allCompilerJars),
      scaladocExtraClasspath = scala.fold(Seq.empty[File])(_.extraJars),
      compilerBridgeBinaryJar = scala.flatMap(_.compilerBridgeBinaryJar),
    )
    new ScalaSdkNode(data)
  }

  private def createModuleExtData(project: sbtStructure.ProjectData, moduleType: ModuleType): ModuleExtNode = {
    val ProjectData(_, _, _, _, _, _, packagePrefix, basePackages, _, _, java, scala, compileOrder, _, _, _, _, _, _, _, _, _) = project

    val scope = moduleType match {
      case TestModuleType => Configuration.Test
      case _ => Configuration.Compile
    }

    def findCompilerOptionsInScope(scope: Configuration, options: Seq[CompilerOptions]): Seq[String] = {
      val matchedCompilerOptions = options.find(_.configuration == scope)
      matchedCompilerOptions.map(_.options).getOrElse(Seq.empty)
    }

    val data = SbtModuleExtData(
      scalaVersion           = scala.map(_.version),
      scalacClasspath        = scala.fold(Seq.empty[File])(_.allCompilerJars),
      scaladocExtraClasspath = scala.fold(Seq.empty[File])(_.extraJars),
      scalacOptions          = findCompilerOptionsInScope(scope, scala.map(_.options).getOrElse(Seq.empty)),
      sdk                    = java.flatMap(_.home).map(home => JdkByHome(home.toPath)),
      javacOptions           = findCompilerOptionsInScope(scope, java.map(_.options).getOrElse(Seq.empty)),
      packagePrefix          = packagePrefix,
      basePackage            = basePackages.headOption, // TODO Rename basePackages to basePackage in sbt-ide-settings?
      compileOrder           = CompileOrder.valueOf(compileOrder)
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

  private def createUnresolvedLibrary(moduleId: sbtStructure.ModuleIdentifier): LibraryNode = {
    val module = sbtStructure.ModuleData(moduleId, Set.empty, Set.empty, Set.empty)
    createLibrary(module, resolved = false)
  }

  private def createResolvedLibrary(module: sbtStructure.ModuleData): LibraryNode = {
    createLibrary(module, resolved = true)
  }

  private def createLibrary(module: sbtStructure.ModuleData, resolved: Boolean): LibraryNode = {
    val result = new LibraryNode(getNameForLibrary(module.id), resolved)
    result.addPaths(LibraryPathType.BINARY, module.binaries.map(_.path).toSeq)
    result.addPaths(LibraryPathType.SOURCE, module.sources.map(_.path).toSeq)
    result.addPaths(LibraryPathType.DOC, module.docs.map(_.path).toSeq)
    result
  }

  private def getNameForLibrary(id: sbtStructure.ModuleIdentifier): String = {
    if (IJ_SDK_CLASSIFIERS.contains(id.classifier)) {
      //DevKit expects IJ SDK library names in certain format for some features to work
      //Examples of resulting library name:
      //  sbt: [IJ-PLUGIN]JetBrains:JUnit:241.13688.18
      //  sbt: [IJ-SDK]org.jetbrains:INTELLIJ-SDK:241.13688.4
      s"[${id.classifier}]${id.organization}:${id.name}:${id.revision}"
    } else {
      val classifierOption = if (id.classifier.isEmpty) None else Some(id.classifier)
      s"${id.organization}:${id.name}:${id.revision}" + classifierOption.map(":" + _).getOrElse("") + s":${id.artifactType}"
    }
  }

  private def createModuleWithAllRequiredDataLegacy(
    project: sbtStructure.ProjectData,
    projectRoot: File,
    moduleName: String,
    moduleGroup: Option[String],
    librariesData: Seq[LibraryData],
    shouldCreateNestedModule: Boolean,
  )(implicit context: ImportContext): PrentModuleSourceSet = {
    // TODO use both ID and Name when related flaws in the External System will be fixed
    // TODO explicit canonical path is needed until IDEA-126011 is fixed
    val projectId = ModuleNode.combinedId(project.id, Option(project.buildURI))
    //NOTE: module name which is passed in ModuleNode constructor will be saved as external module name, module name and
    //additionally as internal name but with all the "/" characters changed to "_"
    val moduleFilesDirectory = createModuleFilesDirectory(projectRoot, project.base)

    val result = createModuleNode(
      StdModuleTypes.JAVA.getId,
      projectId,
      moduleName,
      moduleFilesDirectory,
      project.base.canonicalPath,
      shouldCreateNestedModule
    )
    result.setInheritProjectCompileOutputPath(false)

    result.add(createLegacyContentRoot(project))

    prefixModuleNameWithGroup(result, moduleGroup)

    val projectDependencies = project.dependencies
    addAllRequiredDataToModuleNode(
      librariesData,
      projectDependencies.modules.forProduction,
      projectDependencies.jars.forProduction,
      project,
      result,
      LegacyModuleType,
      moduleName,
      projectDependencies = Seq.empty
    )
    setCompileOutputPathsForLegacyModule(result, project.configurations)

    PrentModuleSourceSet(result)
  }

  /**
   * @return a module name prefixed with a group name.
   */
  private def prefixModuleNameWithGroup(module: ModuleDataNodeType, moduleGroup: Option[String]): String = {
    val moduleNameWithGroup = getInternalModuleNameWithGroup(module, moduleGroup)
    //Using `setInternalName` because there is no way to pass the internal name different from the external name and module name in constructor
    module.setInternalName(moduleNameWithGroup)
    moduleNameWithGroup
  }

  private def createModuleFilesDirectory(projectRoot: File, moduleBase: File): String = {
    val relativeToRoot = FileUtil.getRelativePath(projectRoot, moduleBase)
    val relativePath =
      if (relativeToRoot == null || relativeToRoot.equals(".")) ""
      else relativeToRoot

    val projectRootDirectory = Seq(projectRoot.getName).filter(_.nonEmpty)
    val pathComponents = projectRootDirectory :+ relativePath

    val defaultModuleFilesDir = getDefaultModuleFilesDirectory(projectRoot)
    Path.of(defaultModuleFilesDir, pathComponents: _*).toCanonicalPath.toString
  }

  private def createModuleWithAllRequiredDataForSeparateProdAndTestSources(
    project: sbtStructure.ProjectData,
    projectRoot: File,
    moduleName: String,
    moduleGroup: Option[String],
    librariesData: Seq[LibraryData],
    shouldCreateNestedModule: Boolean,
    sourcesDetails: ProjectSourcesDetails
  )(implicit context: ImportContext): CompleteModuleSourceSet = {
    // TODO use both ID and Name when related flaws in the External System will be fixed
    // TODO explicit canonical path is needed until IDEA-126011 is fixed
    val projectId = ModuleNode.combinedId(project.id, Option(project.buildURI))
    val moduleFilesDirectory = createModuleFilesDirectory(projectRoot, project.base)
    val parentModule = createModuleNode(
      StdModuleTypes.JAVA.getId,
      projectId,
      moduleName,
      moduleFilesDirectory,
      project.base.canonicalPath,
      shouldCreateNestedModule
    )
    val parentModuleNameWithGroup = prefixModuleNameWithGroup(parentModule, moduleGroup)
    addAllRequiredDataToParentModuleNode(project, parentModule)

    val (prodModule, testModule) = createSbtSourceSetModules(
      project,
      moduleFilesDirectory,
      parentModuleNameWithGroup,
    )

    // Set correspondence between Test and Prod modules.
    // IntelliJ can use this information in some actions.
    // For example, it's used when invoking "Create New Test" to understand which test target location to use
    // (see com.intellij.testIntegration.createTest.CreateTestAction.suggestModuleForTests)
    // Note that module internal name is considered a module id
    testModule.setProductionModuleId(prodModule.getInternalName)

    def createDisplayName(module: ModuleDataNodeType): String =
      s"$moduleName.${module.getExternalName}"

    val dependencies = project.dependencies

    val mainContentRoots = createContentRootNodes(sourcesDetails.mainSourceBaseDirectories, sourcesDetails.mainSourceRoots)
    val testContentRoots = createContentRootNodes(sourcesDetails.testSourceBaseDirectories, sourcesDetails.testSourceRoots)

    /*
    It’s possible that a content root pointing to the project base is placed inside the main or test module,
    for example, in cases where the configuration is as follows:
        Compile / sourceDirectory := baseDirectory.value
    In such cases, excluded directories should be added to this content root, and there should be no content root in the parent module.
    */
    val contentRootWithProjectBase = (testContentRoots ++ mainContentRoots).find(_.data.getRootPath == SbtUtil.normalizePath(project.base))
    contentRootWithProjectBase match {
      case Some(contentRoot) => storeExcludedPathsInContentRoot(contentRoot, project)
      case None if sourcesDetails.canCreateParentContentRoot => parentModule.add(createParentContentRoot(project))
      case _ =>
    }

    prodModule.addAll(mainContentRoots)
    testModule.addAll(testContentRoots)

    addAllRequiredDataToModuleNode(
      librariesData,
      dependencies.modules.forProduction,
      dependencies.jars.forProduction,
      project,
      prodModule,
      ProdModuleType,
      createDisplayName(prodModule),
      dependencies.projects.forProduction
    )
    setCompileOutputPaths(
      prodModule,
      project.configurations,
      ExternalSystemSourceType.SOURCE,
      CompileScope,
    )

    addAllRequiredDataToModuleNode(
      librariesData,
      dependencies.modules.forTest,
      dependencies.jars.forTest,
      project,
      testModule,
      TestModuleType,
      createDisplayName(testModule),
      dependencies.projects.forTest
    )
    setCompileOutputPaths(
      testModule,
      project.configurations,
      ExternalSystemSourceType.TEST,
      TestScope,
    )

    parentModule.addAll(Seq(testModule, prodModule))
    addSourceSetModulesDependencies(parentModule, testModule, prodModule)

    CompleteModuleSourceSet(parentModule, prodModule, testModule)
  }

  private def addSourceSetModulesDependencies(parentModule: ModuleDataNodeType, sourceModules: SbtSourceSetModuleNode*): Unit =
    sourceModules.foreach { sourceModuleNode =>
      addModuleDependencyNode(parentModule, sourceModuleNode, DependencyScope.COMPILE, exported = false)
    }

  private def createSbtSourceSetModules(
    project: sbtStructure.ProjectData,
    moduleFilesDirectoryPath: String,
    moduleNameWithGroup: String
  ): (SbtSourceSetModuleNode, SbtSourceSetModuleNode) = {
    def sbtSourceSetModule(sourceSetName: SourceSetType): SbtSourceSetModuleNode = {
      val moduleId = ModuleNode.combinedId(s"${project.id}:$sourceSetName", Option(project.buildURI))
      val moduleNode = new SbtSourceSetModuleNode(
        StdModuleTypes.JAVA.getId,
        moduleId,
        sourceSetName,
        moduleFilesDirectoryPath,
        project.base.canonicalPath
      )
      moduleNode.setInternalName(s"$moduleNameWithGroup.$sourceSetName")
      moduleNode
    }

    (sbtSourceSetModule(SourceSetType.MAIN), sbtSourceSetModule(SourceSetType.TEST))
  }

  private def setCompileOutputPathsForLegacyModule(
    moduleNode: ModuleDataNodeType,
    configurations: Seq[ConfigurationData],
  )(implicit context: ImportContext): Unit =
    Seq((CompileScope, ExternalSystemSourceType.SOURCE), (TestScope, ExternalSystemSourceType.TEST)).foreach { case (scope, sourceType) =>
      setCompileOutputPaths(moduleNode, configurations, sourceType, scope)
    }

  private def setCompileOutputPaths(
    moduleNode: ModuleDataNodeType,
    configurations: Seq[ConfigurationData],
    sourceType: ExternalSystemSourceType,
    scope: String,
  )(implicit context: ImportContext): Unit = {
    def sbtOutputPath(scope: String): Option[String] =
      configurations
        .find(_.id == scope)
        .map(_.classes.path)

    def withIdeaPrefix(path: String): String = {
      val p = Path.of(path)
      val name = p.getFileName
      p.getParent.resolve(s"idea-$name").toString
    }

    moduleNode.setInheritProjectCompileOutputPath(false)
    if (context.useSeparateCompilerOutputPaths) {
      sbtOutputPath(scope).map(withIdeaPrefix).foreach(moduleNode.setCompileOutputPath(sourceType, _))
    } else {
      sbtOutputPath(scope).foreach(moduleNode.setCompileOutputPath(sourceType, _))
    }
  }

  /**
   * @param projectDependencies required to calculate the offset for the library and jar dependencies.
   *                            Currently only useful for the main/test modules mode.
   */
  private def addAllRequiredDataToModuleNode(
    librariesData: Seq[LibraryData],
    moduleDependencies: Seq[ModuleDependencyData],
    jarDependencies: Seq[JarDependencyData],
    projectData: ProjectData,
    moduleNode: ModuleDataNodeType,
    moduleType: ModuleType,
    displayName: String,
    projectDependencies: Seq[_]
  )(implicit context: ImportContext): Unit = {
    moduleNode.add(new SbtDisplayModuleNameNode(displayName))

    // in sbt source set modules task/settings/command are not inserted
    // maybe it should be implemented in the future
    if (moduleType == LegacyModuleType) addSbtRelatedData(projectData, moduleNode)

    // create unmanaged dependencies, we need to know how many of them there are, they need to be ordered before
    // the managed dependencies SCL-21852
    val unmanagedDependencies = createUnmanagedDependencies(jarDependencies)(moduleNode, offset = projectDependencies.size)
    val unmanagedSourcesAndDocsLibrary = librariesData.find(_.getExternalName == Sbt.UnmanagedSourcesAndDocsName)

    val libraryDependenciesNodes = createLibraryDependencies(moduleDependencies)(
      moduleNode,
      librariesData,
      offset = calculateLibraryDepsOffsetMainTestModules(
        unmanagedDependencies,
        unmanagedSourcesAndDocsLibrary,
        projectDependencies
      ),
      useSeparateProdTestSources = context.useSeparateProdTestSources
    )
    moduleNode.addAll(libraryDependenciesNodes)
    moduleNode.add(createModuleExtData(projectData, moduleType))
    moduleNode.add(createScalaSdkData(projectData.scala))
    moduleNode.add(new SbtModuleNode(SbtModuleData(projectData.id, projectData.buildURI, projectData.base)))
    moduleNode.addAll(unmanagedDependencies)
    unmanagedSourcesAndDocsLibrary.foreach { lib =>
      val dependency = new LibraryDependencyNode(moduleNode, lib, LibraryLevel.MODULE)
      // Place the unmanagedSourcesAndDocsLibrary below project dependencies and unmanaged dependencies (but before library dependencies)
      dependency.setOrder(projectDependencies.size + unmanagedDependencies.size + 1)
      dependency.setScope(DependencyScope.COMPILE)
      moduleNode.add(dependency)
    }
  }

  private def addAllRequiredDataToParentModuleNode(
    projectData: ProjectData,
    moduleNode: ModuleDataNodeType,
  )(implicit context: ImportContext): Unit = {
    moduleNode.add(new SbtDisplayModuleNameNode(moduleNode.getModuleName))
    moduleNode.add(new SbtModuleNode(SbtModuleData(projectData.id, projectData.buildURI, projectData.base)))
    addSbtRelatedData(projectData, moduleNode)

    val data = SbtModuleExtData(
      scalaVersion = None,
      sdk = projectData.java.flatMap(_.home).map(home => JdkByHome(home.toPath)),
    )
    moduleNode.add(new ModuleExtNode(data))
  }

  private def addSbtRelatedData(projectData: ProjectData, moduleNode: ModuleDataNodeType): Unit = {
    moduleNode.addAll(createTaskData(projectData))
    moduleNode.addAll(createSettingData(projectData))
    moduleNode.addAll(createCommandData(projectData))
  }

  private case class BuildModuleNodeWithBuildBaseDir(
    moduleNode: ModuleDataNodeType,
    buildBaseDir: File
  )

  private def createBuildModule(
    build: sbtStructure.BuildData,
    projects: Seq[ProjectData],
    defaultModuleFilesDirectory: String,
    localCachePath: Option[String],
    projectToParentModule: Map[ProjectData, ModuleDataNodeType],
    buildProjectsGroups: Seq[BuildProjectsGroup]
  )(implicit context: ImportContext): BuildModuleNodeWithBuildBaseDir = {
    val buildBaseProject =
      projects
        .filter(p => p.buildURI == build.uri)
        .foldLeft(None: Option[ProjectData]) {
          case (None, p) => Some(p)
          case (Some(p), p1) =>
            val parent = if (p.base.isAncestorOf(p1.base)) p else p1
            Some(parent)
        }

    val buildId = buildBaseProject.flatMap(projectToParentModule.get)
      .map(_.getModuleName + Sbt.BuildModuleSuffix)
      .getOrElse(build.uri.toString)

    val buildBaseDir: File = buildBaseProject
      .map(_.base)
      .getOrElse {
        if (build.uri.getScheme == "file") new File(build.uri.getPath)
        else projects.head.base // this really shouldn't happen
      }

    val buildProjectDirRoot = buildBaseDir / Sbt.ProjectDirectory

    val rootNode = findRootNodeForBuild(buildProjectsGroups, buildBaseDir, projectToParentModule)
    val moduleFilesDirectory = rootNode.map(_.getModuleFileDirectoryPath).getOrElse(defaultModuleFilesDirectory)
    // TODO explicit canonical path is needed until IDEA-126011 is fixed
    val result = createModuleNode(
      SbtModuleType.instance.getId, buildId, buildId, moduleFilesDirectory, buildProjectDirRoot.canonicalPath, shouldCreateNestedModule = true
    )

    result.add(new SbtDisplayModuleNameNode(buildId))
    //todo: probably it should depend on sbt version?
    result.add(ModuleSdkNode.inheritFromProject)

    result.setInheritProjectCompileOutputPath(false)
    result.setCompileOutputPath(ExternalSystemSourceType.SOURCE, (buildProjectDirRoot / Sbt.TargetDirectory / "idea-classes").path)
    result.setCompileOutputPath(ExternalSystemSourceType.TEST, (buildProjectDirRoot / Sbt.TargetDirectory / "idea-test-classes").path)
    result.add(createBuildContentRoot(buildProjectDirRoot))

    val library = {
      val classes = build.classes.filter(_.exists).map(_.path)
      val docs = build.docs.filter(_.exists).map(_.path)
      val sources = build.sources.filter(_.exists).map(_.path)
      createModuleLevelDependency(Sbt.BuildLibraryPrefix + context.sbtVersion, classes, docs, sources, DependencyScope.PROVIDED, 0)(result)
    }

    result.add(library)

    result.add(createSbtBuildModuleData(build, projects, localCachePath))

    // note: put build module in a proper project group
    rootNode.foreach(_.add(result))
    val ideModuleGroupNameForBuild = rootNode.map(_.getInternalName)
    val moduleInternalNameWithGroup = getInternalModuleNameWithGroup(result, ideModuleGroupNameForBuild)
    result.setInternalName(moduleInternalNameWithGroup)

    BuildModuleNodeWithBuildBaseDir(result, buildBaseDir)
  }

  /**
   * @param buildProjectDirRoot `myProjectName/project`
   */
  private def createBuildContentRoot(buildProjectDirRoot: File): ContentRootNode = {
    val result = new ContentRootNode(buildProjectDirRoot.path)

    val sourceDirs = Seq(buildProjectDirRoot) // , base << 1

    val excludedDirs = Seq(
      buildProjectDirRoot / Sbt.TargetDirectory,
      buildProjectDirRoot / Sbt.ProjectDirectory / Sbt.TargetDirectory,
    )

    result.storePaths(ExternalSystemSourceType.SOURCE, sourceDirs.map(_.path))
    result.storePaths(ExternalSystemSourceType.EXCLUDED, excludedDirs.map(_.path))

    result
  }

  private def createSbtBuildModuleData(build: sbtStructure.BuildData, projects: Seq[ProjectData], localCachePath: Option[String]): SbtBuildModuleNode = {
    val buildProjects = projects.filter(p => p.buildURI == build.uri)
    val imports = build.imports.flatMap(_.trim.substring(7).split(", "))
    val projectResolvers = buildProjects.flatMap(_.resolvers)
    val resolvers = projectResolvers.map { r => new SbtMavenResolver(r.name, r.root).asInstanceOf[SbtResolver] }

    val resolversAll = resolvers.toSet + localCacheResolver(localCachePath)
    val moduleData = SbtBuildModuleData(imports, resolversAll, build.uri)
    new SbtBuildModuleNode(moduleData)
  }

  private def localCacheResolver(localCachePath: Option[String]): SbtResolver = {
    val localCachePathFinal = localCachePath.getOrElse {
      SystemProperties.getUserHome + "/.ivy2/cache".replace('/', File.separatorChar)
    }
    new SbtIvyResolver("Local cache", localCachePathFinal, isLocal = true, SbtBundle.message("sbt.local.cache"))
  }

  /**
   * Calculate the offset required for library dependencies.
   * This considers unmanaged dependencies, unmanagedSourcesAndDocsLibrary, and project dependencies,
   * as these are all components that should be placed before library dependencies.
   */
  protected def calculateLibraryDepsOffsetMainTestModules(
    unmanagedDependencies: Seq[_],
    unmanagedSourcesAndDocsLibrary: Option[_],
    projectDependencies: Seq[_]
  ): Int =
    unmanagedDependencies.size + unmanagedSourcesAndDocsLibrary.size + projectDependencies.size + 1

  protected def createLibraryDependencies(dependencies: Seq[sbtStructure.ModuleDependencyData])
                                         (moduleData: ModuleData, libraries: Seq[LibraryData], offset: Int, useSeparateProdTestSources: Boolean): Seq[LibraryDependencyNode] = {
    val resolvedDependencies =
      if (!useSeparateProdTestSources) resolveLibraryDependencyConflicts(dependencies)
      else dependencies
    resolvedDependencies.zipWithIndex.map { case (dependency, index) =>
      val name = getNameForLibrary(dependency.id)
      val library = libraries.find(_.getExternalName == name).getOrElse(
        throw new ExternalSystemException("Library not found: " + name))
      val data = new LibraryDependencyNode(moduleData, library, LibraryLevel.PROJECT)
      val order = index + offset
      data.setOrder(order)
      data.setScope(scopeFor(dependency.configurations))
      data
    }
  }

  /**
   * @param offset The unmanaged dependencies should be placed right after the project dependencies,
   *               so the offset should be equal to the size of the project dependencies for a specific module
   */
  protected def createUnmanagedDependencies(dependencies: Seq[sbtStructure.JarDependencyData])
                                           (moduleData: ModuleData, offset: Int = 0): Seq[LibraryDependencyNode] = {
    val scopesAndDeps = dependencies.map(dep => (scopeFor(dep.configurations), dep))
    val groupedByScope = mutable.LinkedHashMap.empty[DependencyScope, Seq[JarDependencyData]]
    scopesAndDeps.foreach { case (scope, dep) =>
      val deps = groupedByScope.getOrElse(scope, Seq.empty)
      groupedByScope(scope) = deps :+ dep
    }

    groupedByScope.toSeq.zipWithIndex.map { case ((scope, dependency), index) =>
      val name = scope match {
        case DependencyScope.COMPILE => Sbt.UnmanagedLibraryName
        case it => s"${Sbt.UnmanagedLibraryName}-${it.getDisplayName.toLowerCase}"
      }
      val files = dependency.map(_.file.path)
      val order = offset + index + 1
      createModuleLevelDependency(name, files, Seq.empty, Seq.empty, scope, order)(moduleData)
    }
  }

  private def createModuleLevelDependency(
    name: String,
    classes: Seq[String],
    docs: Seq[String],
    sources: Seq[String],
    scope: DependencyScope,
    order: Int
  )(moduleData: ModuleData): LibraryDependencyNode = {
    val libraryNode = new LibraryNode(name, resolved = true)
    libraryNode.addPaths(LibraryPathType.BINARY, classes)
    libraryNode.addPaths(LibraryPathType.DOC, docs)
    libraryNode.addPaths(LibraryPathType.SOURCE, sources)

    val result = new LibraryDependencyNode(moduleData, libraryNode, LibraryLevel.MODULE)
    result.setOrder(order)
    result.setScope(scope)
    result
  }

  protected def scopeFor(configurations: Seq[sbtStructure.Configuration]): DependencyScope = {
    val ids = configurations.toSet

    //note: these configuration values are calculated in
    // org.jetbrains.sbt.extractors.DependenciesExtractor.mapConfigurations (it's a separate project)
    if (ids.contains(sbtStructure.Configuration.Compile))
      DependencyScope.COMPILE
    else if (ids.contains(sbtStructure.Configuration.Runtime))
      DependencyScope.RUNTIME //note: in sbt Runtime and Provided dependencies are also automatically included into Test scope
    else if (ids.contains(sbtStructure.Configuration.Provided))
      DependencyScope.PROVIDED
    else if (ids.contains(sbtStructure.Configuration.Test))
      DependencyScope.TEST
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

  val CompileScope = "compile"
  val TestScope = "test"
  val IntegrationTestScope = "it"

  private val IJ_SDK_CLASSIFIERS: Set[String] = Set("IJ-SDK", "IJ-PLUGIN")

  case class ImportCancelledException(cause: Throwable) extends Exception(cause)

  //I know that it's a hacky dirty solution, but it's sufficient for now
  //It's hard to access process output from tests, because we use quite high-level project import API in tests
  @TestOnly
  @ApiStatus.Internal
  var processOutputOfLatestStructureDump: String = ""

  private case class LibraryIdentifierWithoutRevision(
    organization: String,
    name: String,
    artifactType: String,
    classifier: String
  )
  private object LibraryIdentifierWithoutRevision {
    def from(id: ModuleIdentifier): LibraryIdentifierWithoutRevision =
      LibraryIdentifierWithoutRevision(id.organization, id.name, id.artifactType, id.classifier)
  }

  /**
   * In case there are several dependencies (usually transitive) on same library but with different versions we leave one "best" dependency.<br>
   * Otherwise, it can lead to various classpath-related issues at runtime (e.g. SCL-19878, SCL-18952)
   *
   * Note, that this basic conflict managing process is far from what is implemented in SBT.
   * For example SCL-18952 is not fixed "fairly".
   * But it's at least better then nothing, it helps avoiding multiple jars of same library in the classpath.
   *
   * Note that sbt has separate set of classpath for each scope, which can be obtained using {{{
   *   show Compile / dependencyClasspathAsJars
   *   show Runtime / dependencyClasspathAsJars
   *   show Test/ dependencyClasspathAsJars
   * }}}
   * And right now we can't fully emulate this with IntelliJ model, which implies single dependency on same library.
   *
   * Though in future we could move this "conflicts resolving" to the runtime, when program is being executed and hold multiple dependencies on same library in the model.
   * It would require patching UI for `Project settings | Modules | Dependencies`
   *
   * @param dependencies library dependencies with potential conflicting versions
   * @return library dependencies where all conflicting library versions are replaces with a single "best" library dependency.
   * @note it emulates the default sbt behaviour when "latest revision is selected".
   *       If in sbt build definition some non-default conflictManager is set, this may behave not as expected<br>
   *       (see https://www.scala-sbt.org/1.x/docs/Library-Management.html#Conflict+Management)
   */
  @TestOnly
  def resolveLibraryDependencyConflicts(dependencies: Seq[sbtStructure.ModuleDependencyData]): Seq[sbtStructure.ModuleDependencyData] = {
    val libToConflictingDeps: Map[LibraryIdentifierWithoutRevision, Seq[ModuleDependencyData]] =
      dependencies.groupBy(d => LibraryIdentifierWithoutRevision.from(d.id)).filter(_._2.size > 1)

    val libToBestDependencyData: MapView[LibraryIdentifierWithoutRevision, ModuleDependencyData] =
      libToConflictingDeps.view.mapValues(calculateBestDependency)

    val alreadyResolvedConflicts = mutable.Set.empty[LibraryIdentifierWithoutRevision]
    dependencies.flatMap { dep =>
      val ortArtName = LibraryIdentifierWithoutRevision.from(dep.id)
      libToBestDependencyData.get(ortArtName) match {
        case None => Some(dep)
        case Some(value) =>
          if (alreadyResolvedConflicts.contains(ortArtName))
            None
          else {
            alreadyResolvedConflicts += ortArtName
            Some(value)
          }
      }
    }
  }

  /**
   * Return dependency with max library version and "max" scope. Note, that scopes do not have a strict order.
   * The most problematic part is that we can't directly compare "Provided" and "Runtime" scopes.
   * They have completely opposite semantics. But here we assume that "Provided" > "Runtime".
   *
   * @note anyway in general we can't 100% emulate SBT dependencies & classpath model with current IntelliJ model
   * @note in sbt, Provided & Runtime scopes are automatically added to the "Test" scope, so "Test" has the lowest priority.
   */
  private def calculateBestDependency(conflictingDependencies: Seq[ModuleDependencyData]): ModuleDependencyData = {
    val dependencyWithMaxVersion = conflictingDependencies.maxBy(d => Version(d.id.revision))

    val maxConfigurationOpt = conflictingDependencies.iterator.flatMap(_.configurations).maxByOption {
      case Configuration.Compile => 4
      case Configuration.Provided => 3
      case Configuration.Runtime => 2
      case Configuration.Test => 1
      case _ => 0
    }

    ModuleDependencyData(
      dependencyWithMaxVersion.id,
      maxConfigurationOpt.map(Seq(_)).getOrElse(dependencyWithMaxVersion.configurations)
    )
  }

  private sealed trait ModuleType
  private sealed trait NewModuleType extends ModuleType
  private final case object LegacyModuleType extends ModuleType
  private final case object ProdModuleType extends NewModuleType
  private final case object TestModuleType extends NewModuleType

  /**
   * Contains some options that are actual and unchanged for the whole import process, for all modules
   */
  private[project] case class ImportContext(
    executionSettings: SbtExecutionSettings,
  ) {
    def sbtVersion: SbtVersion = executionSettings.sbtVersion
    def useSeparateProdTestSources: Boolean = executionSettings.separateProdTestSources
    def useSeparateCompilerOutputPaths: Boolean = executionSettings.useSeparateCompilerOutputPaths
  }
}
