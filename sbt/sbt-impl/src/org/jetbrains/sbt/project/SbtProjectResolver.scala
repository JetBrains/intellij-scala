package org.jetbrains.sbt.project

import com.intellij.build.issue.{BuildIssue, BuildIssueQuickFix}
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.project.LibraryData
import com.intellij.openapi.externalSystem.model.task.event.{Failure as ESFailure, *}
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.model.{DataNode, ExternalSystemException, project as esProjectData}
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager}
import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.{Registry, RegistryManager}
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.EelProviderUtil
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.platform.eel.provider.utils.EelPathUtils.TransferTarget
import com.intellij.pom.Navigatable
import com.intellij.util.SystemProperties
import org.jetbrains.annotations.{ApiStatus, NonNls, Nullable, TestOnly}
import org.jetbrains.plugins.scala.*
import org.jetbrains.plugins.scala.build.*
import org.jetbrains.plugins.scala.compiler.data.CompileOrder
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.project.external.{JdkByHome, JdkByName, ScalaSdkUtils, SdkReference}
import org.jetbrains.plugins.scala.project.{ReplClasspath, Version}
import org.jetbrains.plugins.scala.util.ScalaNotificationGroups
import org.jetbrains.sbt.SbtUtil.*
import org.jetbrains.sbt.process.SbtProcessOutputDiagnosticsCollector.PrintProcessOutputOnFailurePropertyName
import org.jetbrains.sbt.process.{SbtImportTimingCollector, SbtRunner}
import org.jetbrains.sbt.project.SbtProjectResolver.*
import org.jetbrains.sbt.project.SbtProjectResolver.ImportContext.given
import org.jetbrains.sbt.project.data.*
import org.jetbrains.sbt.project.versionNotifications.LegacySbtVersionBuildToolWindowWarning
import org.jetbrains.sbt.project.module.SbtModuleType
import org.jetbrains.sbt.project.settings.*
import org.jetbrains.sbt.project.structure.data.*
import org.jetbrains.sbt.project.structure.data.XmlDeserializer.deserialize
import org.jetbrains.sbt.project.structure.{Play2OldStructureAdapter, SbtStructureDumper, data as sbtStructure}
import org.jetbrains.sbt.resolvers.{SbtIvyResolver, SbtMavenResolver, SbtResolver}
import org.jetbrains.sbt.{RichBoolean, Sbt, SbtBundle, SbtUtil, SbtVersion, usingTempFile}

import java.io.FileNotFoundException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.CompletableFuture
import java.util.{Collections, Locale, UUID, List as JList}
import scala.collection.{MapView, mutable}
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, TimeoutException}
import scala.util.{Failure, Random, Success, Try}
import scala.xml.{Elem, XML}

/**
 * @see [[com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver]]
 * @see [[com.intellij.openapi.externalSystem.service.remote.wrapper.ExternalSystemProjectResolverWrapper]]
 */
class SbtProjectResolver extends ExternalSystemProjectResolver[SbtExecutionSettings] with ExternalSourceRootResolution with ContentRootsResolution {

  private val log = Logger.getInstance(getClass)

  @volatile private var activeProcessDumper: Option[SbtStructureDumper] = None

  override def resolveProjectInfo(
    taskId: ExternalSystemTaskId,
    wrongProjectPathDontUseIt: String,
    isPreview: Boolean,
    settings: SbtExecutionSettings,
    listener: ExternalSystemTaskNotificationListener
  ): DataNode[esProjectData.ProjectData] = {
    val projectRoot = {
      val file = Path.of(settings.realProjectPath)
      if (file.isDirectory) file else file.getParent
    }

    val sbtLauncher = SbtUtil.getLauncherJar(settings)

    @Nullable val ideaProject: Project = taskId.findProject()
    val useShellImport = settings.useShellForImport && ideaProject != null

    val eelDescriptor = EelProviderUtil.getEelDescriptor(projectRoot)
    val timingCollector =
      if (!useShellImport && Registry.is("sbt.import.time.measurement")) Some(new SbtImportTimingCollector.TimingCollector(projectRoot))
      else None
    implicit val context: ImportContext = ImportContext(settings, eelDescriptor, timingCollector)

    if (isPreview) dummyProject(projectRoot, settings).toDataNode
    else {
      // An indicator will always exist (be not null) when using the External System machinery.
      // Gradle also relies on this.
      val indicator = ProgressManager.getInstance().getProgressIndicator
      if (indicator == null) {
        throw new IllegalStateException("The External System machinery did not provide a ProgressIndicator instance")
      }
      importProject(taskId, settings, projectRoot, sbtLauncher, listener, indicator, ideaProject, useShellImport)
    }
  }

  private def importProject(
    taskId: ExternalSystemTaskId,
    settings: SbtExecutionSettings,
    projectRoot: Path,
    sbtLauncher: Path,
    notifications: ExternalSystemTaskNotificationListener,
    indicator: ProgressIndicator,
    @Nullable ideaProject: Project,
    useShellImport: Boolean
  )(implicit context: ImportContext): DataNode[esProjectData.ProjectData] = {

    @NonNls val importTaskId = s"import:${UUID.randomUUID()}"
    val importTaskDescriptor =
      new TaskOperationDescriptor(SbtBundle.message("sbt.import.to.intellij.project.model"), System.currentTimeMillis(), "project-model-import")

    val esReporter = new ExternalSystemNotificationReporter(projectRoot.toCanonicalPath.toString, taskId, notifications)
    implicit val reporter: BuildReporter = if (isUnitTestMode) {
      val logReporter = new LogReporter
      new CompositeReporter(esReporter, logReporter)
    } else esReporter

    val startTime = System.currentTimeMillis()
    val structureDump = dumpStructure(projectRoot, sbtLauncher, context.sbtVersion, settings, ideaProject, indicator, useShellImport)

    // side-effecty status reporting
    structureDump.foreach { _ =>
      val convertStartEvent = new ExternalSystemStartEvent(importTaskId, null, importTaskDescriptor)
      val event = new ExternalSystemTaskExecutionEvent(taskId, convertStartEvent)
      notifications.onStatusChange(event)
    }

    val conversionResult: Try[DataNode[esProjectData.ProjectData]] = structureDump
      .map { case (elem, _) =>
        val data = elem.deserialize[sbtStructure.StructureData]
          .fold(
            e => throw new IllegalStateException("Could not deserialize sbt structure data", e),
            identity
          )

        val convertStart = System.currentTimeMillis()
        val result = convert(normalizePath(projectRoot), data, settings.jdk, settings, Option(ideaProject), useShellImport).toDataNode
        val convertTime = System.currentTimeMillis - convertStart

        try {
          context.timingCollector.foreach { timingCollector =>
            timingCollector.addScalaPluginTimings("convert" -> convertTime)
            timingCollector.writeTimingResults()
          }
        } catch {
          case ex: Exception => log.warn("Failed to write timing summary with custom timings", ex)
        }

        result
      }
      .recoverWith {
        case ImportCancelledException(cause) =>
          val causeMessage = if (cause != null) cause.getMessage else SbtBundle.message("sbt.unknown.cause")

          // notify user if project exists already
          val projectOpt = ProjectManager.getInstance().getOpenProjects.find(p => FileUtil.pathsEqual(p.getBasePath, projectRoot.toCanonicalPath.toString))
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
    projectRoot: Path,
    sbtLauncher: Path,
    sbtVersion: SbtVersion,
    settings: SbtExecutionSettings,
    @Nullable project: Project,
    indicator: ProgressIndicator,
    useShellImport: Boolean
  )(implicit reporter: BuildReporter, context: ImportContext): Try[(Elem, BuildMessages)] = {
    if (isUnitTestMode) {
      SbtProjectResolver.setProcessOutputOfLatestStructureDump("")
    }

    val optString = makeOptionsStringLiteral(settings)

    def doDumpStructure(structureFile: Path): Try[(Elem, BuildMessages)] = {
      val dumper =
        if useShellImport then SbtStructureDumper.FromShell()
        else SbtStructureDumper.FromProcess()

      activeProcessDumper = Option(dumper)

      val transferredStructureFile =
        EelPathUtils.transferLocalContentToRemote(structureFile, TransferTarget.Temporary(context.eelDescriptor))

      val messageResult: Try[BuildMessages] = {
        dumper match {
          case sd: SbtStructureDumper.FromShell =>
            val messagesF = sd.dumpFromShell(
              project,
              transferredStructureFile,
              optString,
              reporter,
              settings.preferScala2
            )
            Try {
              val testTimeout =
                if (isUnitTestMode) SbtRunner.MaxImportDurationInUnitTests
                else Duration.Inf // TODO some kind of timeout / cancel mechanism

              try Await.result(messagesF, testTimeout)
              catch {
                case _: TimeoutException if isUnitTestMode =>
                  throw new TimeoutException(s"sbt-shell import hasn't finished in ${SbtRunner.MaxImportDurationInUnitTests}")
              }
            }

          case pd: SbtStructureDumper.FromProcess =>
            val sbtStructureJar = settings
              .customSbtStructureFile
              .map(_.toPath)
              .orElse(SbtUtil.getSbtStructureJar(sbtVersion, context.repoDir))
              .getOrElse(throw new ExternalSystemException(s"Could not find sbt-structure-extractor for sbt version $sbtVersion"))

            log.debug(s"sbtStructureJar: $sbtStructureJar")
            // TODO add error/warning messages during dump, report directly
            pd.dumpFromProcess(
              indicator,
              projectRoot,
              transferredStructureFile,
              optString,
              settings.vmExecutable.toPath,
              settings.vmOptions,
              settings.sbtOptions,
              settings.userSetEnvironment,
              sbtLauncher,
              sbtStructureJar,
              settings.preferScala2,
              settings.passParentEnvironment,
              Option(project)
            )
        }
      }
      activeProcessDumper = None

      copyFileContentsIfNeeded(transferredStructureFile, structureFile)

      val result: Try[(Elem, BuildMessages)] = messageResult.flatMap { messages =>
        val tried = {
          def failure(reason: String): Failure[(Elem, BuildMessages)] = {
            val message = SbtBundle.message("sbt.import.extracting.structure.failed") + s": $reason"
            Failure(new Exception(message))
          }

          if (messages.status != BuildMessages.OK)
            failure(SbtBundle.message("sbt.import.message.build.status", messages.status))
          else if (!structureFile.isRegularFile)
            failure(SbtBundle.message("sbt.import.message.structure.file.is.not.a.file", structureFile.toCanonicalPath.toString))
          else if (Files.size(structureFile) <= 0)
            failure(SbtBundle.message("sbt.import.message.structure.file.is.empty", structureFile.toCanonicalPath.toString))
          else Try {
            val elem = XML.load(structureFile.toUri.toURL)
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
        SbtProjectResolver.setProcessOutputOfLatestStructureDump(processOutput)
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

    if (!sbtLauncher.isRegularFile) {
      val error = SbtBundle.message("sbt.launcher.not.found", sbtLauncher.toCanonicalPath.toString)
      Failure(new FileNotFoundException(error))
    } else {
      LegacySbtVersionBuildToolWindowWarning.warnForBuildToolWindowIfNeeded(project, projectRoot, sbtVersion, reporter)

      if (!settings.separateProdTestSources) {
        LegacyModulesLayoutNotifications.warnForBuildToolWindow(reporter)
      }

      if (context.timingCollector.nonEmpty) {
        informAboutImportTimingEnabled(reporter)
      }

      val structureFilePath = getStructureFilePath(projectRoot)
      val StructureFileReuseMode(readStructureFile, writeStructureFile) = getStructureFileReuseMode

      if (readStructureFile && structureFilePath.exists(_.exists)) {
        val reuseWarning = s"sbt reload skipped: using existing structure file: $structureFilePath"
        log.warn(reuseWarning)
        //noinspection ReferencePassedToNls (this branch is only triggered when registry was explicitly modified, so it's not i18-ed)
        reporter.log(reuseWarning)
        val elem = XML.load(structureFilePath.get.toUri.toURL)
        Try((elem, BuildMessages.empty))
      } else if (writeStructureFile && structureFilePath.nonEmpty) {
        log.warn(s"reused structure file created: $structureFilePath")
        doDumpStructure(structureFilePath.get)
      } else {
        SbtProjectResolver.withStructureFile { structureFile =>
          doDumpStructure(structureFile)
        }
      }
    }
  }

  private def copyFileContentsIfNeeded(remotePath: Path, localPath: Path): Unit =
    import java.io.PrintWriter
    import java.nio.charset.StandardCharsets.UTF_8
    import java.nio.file.Files
    import java.nio.file.StandardOpenOption.*
    import scala.util.Using
    if remotePath != localPath then
      Using.resource(Files.newBufferedReader(remotePath, UTF_8)): reader =>
        Using.resource(PrintWriter(Files.newBufferedWriter(localPath, UTF_8, CREATE, TRUNCATE_EXISTING, WRITE))): writer =>
          reader.lines().forEach(writer.println(_))

  private def informAboutImportTimingEnabled(buildReporter: BuildReporter): Unit = {
    val quickFixId = "disable_import_timing"
    val quickFix: BuildIssueQuickFix = new BuildIssueQuickFix {
      override def getId: String = quickFixId

      override def runQuickFix(project: Project, dataContext: DataContext): CompletableFuture[?] = {
        Registry.get("sbt.import.time.measurement").setValue(false)
        CompletableFuture.completedFuture(null)
      }
    }

    val buildIssue: BuildIssue = new BuildIssue {
      override def getTitle: String = SbtBundle.message("sbt.import.timing.title")
      override def getDescription: String = SbtBundle.message("sbt.import.timing.details", quickFixId)
      override def getQuickFixes: JList[BuildIssueQuickFix] = JList.of(quickFix)
      override def getNavigatable(project: Project): Navigatable = null
    }
    buildReporter.info(buildIssue)
  }

  private def getStructureFilePath(projectRoot: Path): Option[Path] =
    Option(System.getProperty("sbt.project.structure.location"))
      .map(Path.of(_))
      .map:
        case dir if !dir.isAbsolute => projectRoot.resolve(dir).toCanonicalPath
        case dir => dir
      .map(_ / s"sbt-structure-reused-${projectRoot.getFileName.toString}.xml")

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

  private def makeOptionsStringLiteral(settings: SbtExecutionSettings): String = {
    val options =
      Seq("download") ++
        settings.resolveClassifiers.seq("resolveSourceClassifiers") ++
        settings.resolveSbtClassifiers.seq("resolveSbtClassifiers") ++
        settings.separateProdTestSources.seq("separateProdAndTestSources") ++
        settings.generateManagedSourcesDuringProjectSync.seq("generateManagedSources")

    options.mkString("\"", ", ", "\"")
  }

  /**
   * Create project preview without using sbt, since sbt import can fail and users would have to do a manual edit of the project.
   * Also sbt boot makes the whole process way too slow.
   */
  private def dummyProject(
    projectRoot: Path,
    settings: SbtExecutionSettings,
  )(implicit context: ImportContext): Node[esProjectData.ProjectData] = {

    // TODO add default scala sdk and sbt libs (newest versions or so)

    val projectUri = projectRoot.toUri
    val projectPath = projectRoot.toCanonicalPath.toString
    val projectName = normalizeModuleId(projectRoot.getFileName.toString)
    val projectNameNumberSuffix =
      if (ApplicationManager.getApplication.isUnitTestMode) PreviewImportNumberSuffixInTests
      else Random.nextInt(10000)
    val projectTmpName = projectName + "_" + projectNameNumberSuffix
    val sourceDir = projectRoot / "src" / "main" / "scala"
    val classDir = projectRoot / "target" / "dummy"

    given PathConstructor[Path]:
      override def construct(path: Path): InterpretablePath =
        new InterpretablePath(path.toCanonicalPath.toString)

    val dummyConfigurationData = ConfigurationData(CompileScope, Seq(DirectoryData(InterpretablePath.construct(sourceDir), managed = false)), Seq.empty, Seq.empty, InterpretablePath.construct(classDir))
    val dummyJavaData = JavaData(None, Seq.empty)
    val dummyDependencyData = sbtStructure.DependencyData(Dependencies(Seq.empty, Seq.empty), Dependencies(Seq.empty, Seq.empty), Dependencies(Seq.empty, Seq.empty))
    val dummyRootProject = ProjectData(
      projectTmpName, projectUri, projectTmpName, s"org.$projectName", "0.0", InterpretablePath.construct(projectRoot), None, Seq.empty,
      InterpretablePath.construct(projectRoot / "target"), Seq(dummyConfigurationData), Option(dummyJavaData), None, None, CompileOrder.Mixed.toString,
      dummyDependencyData, Set.empty, None, Seq.empty, Seq.empty, Seq.empty, mainSourceDirectories = Seq(InterpretablePath.construct(projectRoot / "src" / "main")),
      Seq(), generatedManagedSources = false
    )

    val projects = Seq(dummyRootProject)

    val projectNode = new ProjectNode(projectName, projectPath, projectPath)
    val libraryData = Map.empty[String, LibraryData]
    val buildProjectsGroup = Seq(BuildProjectsGroup(projectUri, dummyRootProject, Nil, projectTmpName))
    val projectToModule = createIntelliJModuleNodes(
      buildProjectsGroup,
      groupedSharedRoots = Nil,
      libraryData,
      projectRoot
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
      buildProjectsGroup,
      isPreview = true,
      useShellImport = false
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
    optIdeaProject: Option[Project],
    useShellImport: Boolean
  )(implicit context: ImportContext): Node[esProjectData.ProjectData] = {

    /**
     * A copy of [[FileUtil.filesEqual]] but without using `java.io.File`.
     */
    def filesEqual(@Nullable file1: Path, @Nullable file2: Path): Boolean =
      val path1 = if file1 == null then null else file1.toCanonicalPath.toString
      val path2 = if file2 == null then null else file2.toCanonicalPath.toString
      FileUtil.pathsEqual(path1, path2)

    val projects: Seq[sbtStructure.ProjectData] = data.projects
    val projectRootFile = Path.of(root)
    val rootProject: sbtStructure.ProjectData =
      projects.find(p => filesEqual(p.base.toPath, projectRootFile))
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

    val newPlay2Data = projects.flatMap(p => p.play2.map(d => (p.id, p.base.toPath, d)))
    projectNode.add(new Play2ProjectNode(Play2OldStructureAdapter(newPlay2Data)))

    val projectLibraryNodes = createLibraries(data, projects)
    projectNode.addAll(projectLibraryNodes)

    val groupedSharedRoots = groupSharedRoots(projects, projectRootFile)

    val buildProjectsGroups: Seq[BuildProjectsGroup] = createBuildProjectGroups(projects)
    val libraryDataByName = projectLibraryNodes.map(lib => lib.getExternalName -> lib.data).toMap
    val projectToModule: Map[ProjectData, ModuleSourceSet] = createIntelliJModuleNodes(
      buildProjectsGroups,
      groupedSharedRoots,
      libraryDataByName,
      projectRootFile
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
      libraryDataByName,
      defaultModuleFilesDirectory,
      settings.separateProdTestSources,
      buildProjectsGroups
    )

    val buildModuleForProject: BuildData => BuildModuleNodeWithBuildBaseDir =
      build => createBuildModule(
        build,
        projects,
        defaultModuleFilesDirectory,
        data.localCachePath.map(_.toPath.toCanonicalPath.toString),
        projectToParentModule,
        buildProjectsGroups,
        isPreview = false,
        useShellImport
      )
    val buildModules = data.builds.map(buildModuleForProject)

    configureBuildModuleDependencies(buildModules)

    notifyGeneratedManagedSources(root, data, optIdeaProject)

    projectNode
  }

  private def removeNestedModuleNodes(nodes: Seq[ModuleDataNodeType]): Seq[ModuleDataNodeType] =
    nodes.filterNot(_.isInstanceOf[NestedModuleNode])

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
  private def configureBuildModuleDependencies(buildModules: Seq[BuildModuleNodeWithBuildBaseDir]): Unit = buildModules match {
    case Seq(module1, module2) =>
      if (isChild(module1.buildBaseDir, module2.buildBaseDir)) {
        addModuleDependencyNode(module2.moduleNode, module1.moduleNode, DependencyScope.COMPILE)
      }
      else if (isChild(module2.buildBaseDir, module1.buildBaseDir)) {
        addModuleDependencyNode(module1.moduleNode, module2.moduleNode, DependencyScope.COMPILE)
      }
      else {
        //modules are not hierarchical? Not sure if such case possible but will leave the empty branch here
      }

    // See https://youtrack.jetbrains.com/issue/SCL-24902/Unify-the-Scala-Ultimate-and-Scala-Community-sbt-builds.
    // This is an extremely specific workaround for the Scala Plugin for IntelliJ IDEA Ultimate repository.
    // We set the <root>/community/project directory as a source directory for the "scalaUltimate-build" build module.
    case Seq(buildModule) if buildModule.moduleNode.getModuleName == "scalaUltimate-build" =>
      val buildBaseDir = buildModule.buildBaseDir
      val communityProjectDir = buildBaseDir / "community" / Sbt.ProjectDirectory
      if (communityProjectDir.exists) { // The community directory exists
        val buildSbtSourcesPatchFile = buildBaseDir / Sbt.ProjectDirectory / "build.sbt"
        if (buildSbtSourcesPatchFile.exists) { // <root>/project/build.sbt exists
          //noinspection ApiStatus,UnstableApiUsage
          val contents = com.intellij.platform.eel.fs.EelFiles.readString(buildSbtSourcesPatchFile, StandardCharsets.UTF_8)
          val containsPatch = contents.contains("""Compile / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "community" / "project"""")
          if (containsPatch) {
            buildModule.moduleNode.add(createBuildContentRootForScalaPluginUltimateWorkaround(communityProjectDir))
          }
        }
      }

    case _ =>
  }

  private def isChild(child: Path, parentPath: Path): Boolean = {
    val parent = parentPath.normalize()
    child.normalize().startsWith(parent)
  }

  /** Choose a project jdk based on information from sbt settings and IDE.
   * More specific settings from sbt are preferred over IDE settings, on the assumption that the sbt project definition
   * is what is more likely to be under source control.
   */
  private def chooseJdk(project: sbtStructure.ProjectData, defaultJdk: Option[String])(using context: ImportContext): Option[SdkReference] = {
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
    libraryDataByName: Map[String, LibraryData],
    projectRoot: Path,
  )(implicit context: ImportContext): Map[ProjectData, ModuleSourceSet] = {
    val projectsSourcesDetails =
      if (context.useSeparateProdTestSources) resolveProjectsSourcesDetails(projectsGrouped, groupedSharedRoots)
      else Map.empty[ProjectData, ProjectSourcesDetails]

    val projectToModule: Iterable[(ProjectData, ModuleSourceSet)] = projectsGrouped.flatMap { buildProjectsGroup =>
      createModulesInsideBuildProjectGroup(
        buildProjectsGroup,
        projectRoot,
        libraryDataByName,
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
    projectRoot: Path,
    libraryDataByName: Map[String, esProjectData.LibraryData],
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
          project, projectRoot, moduleName, moduleGroup, libraryDataByName, shouldCreateNestedModule, projectSourcesDetails
        )
      } else {
        createModuleWithAllRequiredDataLegacy(
          project, projectRoot, moduleName, moduleGroup, libraryDataByName, shouldCreateNestedModule
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

  private def createBuildProjectGroups(projects: Seq[ProjectData])(using context: ImportContext): Seq[BuildProjectsGroup] = {
    val buildToProjects: Map[URI, Seq[ProjectData]] =
      projects.groupBy(_.buildURI)

    //NOTE: sort by URI for a better reproducibility/testability of the resulting project structure
    //The matters for unique group names generation
    //(if the order is not specified, group names of projects with colliding names can have random index suffixes)
    buildToProjects
      .toSeq.sortBy(_._1)
      .map { case (buildUri, projects) =>
        val rootProject = findRootProjectInBuild(projects, buildUri)
        val projectsWithoutRootProject = projects.filterNot(_ == rootProject)
        BuildProjectsGroup(buildUri, rootProject, projectsWithoutRootProject, rootProject.name)
      }
  }

  private def findRootProjectInBuild(projectInSameBuild: Seq[ProjectData], buildURI: URI)(using context: ImportContext): ProjectData = {
    // In most cases, projects within a single sbt build cannot be declared outside the project root,
    // whether by absolute or relative paths. The exception to this rule is when projects are declared using symlinks (SCL-24216).
    // That's why the root project is first attempted to be found via the buildPath,
    // and as a fallback, the shortest path is used.
    val buildPath = Try(Path.of(buildURI)).toOption
    val projectAtBuildPath = buildPath.flatMap { path =>
      projectInSameBuild.find(_.base.toPath == path)
    }
    projectAtBuildPath.getOrElse {
      projectInSameBuild.minBy(_.base.toPath.toCanonicalPath.toString.length)
    }
  }

  private def createLibraries(data: sbtStructure.StructureData, projects: Seq[sbtStructure.ProjectData])(using context: ImportContext): Seq[LibraryNode] = {
    val repositoryModules = data.repository.map(_.modules).getOrElse(Seq.empty)
    val (modulesWithoutBinaries, modulesWithBinaries) = repositoryModules.partition(_.binaries.isEmpty)
    val modulesFromProjects = projects.flatMap { proj =>
      val dependencies = proj.dependencies.modules
      val prodAndTest = dependencies.forProduction ++ dependencies.forTest
      prodAndTest.map(_.id)
    }.distinct
    val otherModuleIds = modulesFromProjects.diff(repositoryModules.map(_.id))

    val libs = modulesWithBinaries.map(createResolvedLibrary) ++ otherModuleIds.map(createUnresolvedLibrary)

    val modulesWithDocumentation = modulesWithoutBinaries.filter(m => m.docs.nonEmpty || m.sources.nonEmpty)
    if (modulesWithDocumentation.isEmpty) return libs

    val unmanagedSourceLibrary = new LibraryNode(Sbt.UnmanagedSourcesAndDocsName, true)
    unmanagedSourceLibrary.addPaths(esProjectData.LibraryPathType.DOC, modulesWithDocumentation.flatMap(_.docs).map(_.toPath.toCanonicalPath.toString))
    unmanagedSourceLibrary.addPaths(esProjectData.LibraryPathType.SOURCE, modulesWithDocumentation.flatMap(_.sources).map(_.toPath.toCanonicalPath.toString))
    libs :+ unmanagedSourceLibrary
  }

  protected def createScalaSdkData(scala: Option[ScalaData])(using context: ImportContext): ScalaSdkNode = {
    val replClasspath = scala.map(_.version).map(ScalaSdkUtils.resolveReplClasspath(context.eelDescriptor, _)).getOrElse(ReplClasspath.Bundled)

    val data = SbtScalaSdkData(
      scalaVersion = scala.map(_.version),
      scalacClasspath = scala.fold(Seq.empty[Path])(_.allCompilerJars.map(_.toPath)),
      scaladocExtraClasspath = scala.fold(Seq.empty[Path])(_.extraJars.map(_.toPath)),
      compilerBridgeBinaryJar = scala.flatMap(_.compilerBridgeBinaryJar.map(_.toPath)),
      replClasspath = replClasspath
    )
    new ScalaSdkNode(data)
  }

  private def createModuleExtData(project: sbtStructure.ProjectData, moduleType: ModuleType)(using context: ImportContext): ModuleExtNode = {
    val ProjectData(_, _, _, _, _, _, packagePrefix, basePackages, _, _, javaData, scala, kotlin, compileOrder, _, _, _, _, _, _, _, _, _) = project

    val scope = moduleType match {
      case TestModuleType => Configuration.Test
      case _ => Configuration.Compile
    }

    def findCompilerOptionsInScope(scope: Configuration, options: Seq[CompilerOptions]): Seq[String] = {
      val matchedCompilerOptions = options.find(_.configuration == scope)
      matchedCompilerOptions.map(_.options).getOrElse(Seq.empty)
    }

    val data = SbtModuleExtData(
      scalacOptions          = findCompilerOptionsInScope(scope, scala.map(_.options).getOrElse(Seq.empty)),
      sdk                    = javaData.flatMap(_.home).map(home => JdkByHome(home.toPath)),
      javacOptions           = findCompilerOptionsInScope(scope, javaData.map(_.options).getOrElse(Seq.empty)),
      kotlincOptions         = findCompilerOptionsInScope(scope, kotlin.map(_.options).getOrElse(Seq.empty)),
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

  private def createUnresolvedLibrary(moduleId: sbtStructure.ModuleIdentifier)(using ImportContext): LibraryNode = {
    val module = sbtStructure.ModuleData(moduleId, Set.empty, Set.empty, Set.empty)
    createLibrary(module, resolved = false)
  }

  private def createResolvedLibrary(module: sbtStructure.ModuleData)(using ImportContext): LibraryNode = {
    createLibrary(module, resolved = true)
  }

  private def createLibrary(module: sbtStructure.ModuleData, resolved: Boolean)(using context: ImportContext): LibraryNode = {
    val sbtModuleId = module.id

    val result = new LibraryNode(getNameForLibrary(sbtModuleId), resolved)
    result.addPaths(esProjectData.LibraryPathType.BINARY, module.binaries.map(_.toPath.toCanonicalPath.toString).toSeq)
    result.addPaths(esProjectData.LibraryPathType.SOURCE, module.sources.map(_.toPath.toCanonicalPath.toString).toSeq)
    result.addPaths(esProjectData.LibraryPathType.DOC, module.docs.map(_.toPath.toCanonicalPath.toString).toSeq)

    result.data.setGroup(sbtModuleId.organization)
    result.data.setArtifactId(sbtModuleId.name)
    result.data.setVersion(sbtModuleId.revision)

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
    projectRoot: Path,
    moduleName: String,
    moduleGroup: Option[String],
    libraryDataByName: Map[String, esProjectData.LibraryData],
    shouldCreateNestedModule: Boolean,
  )(implicit context: ImportContext): PrentModuleSourceSet = {
    // TODO use both ID and Name when related flaws in the External System will be fixed
    // TODO explicit canonical path is needed until IDEA-126011 is fixed
    val projectId = ModuleNode.combinedId(project.id, Option(project.buildURI))
    //NOTE: module name which is passed in ModuleNode constructor will be saved as external module name, module name and
    //additionally as internal name but with all the "/" characters changed to "_"
    val moduleFilesDirectory = createModuleFilesDirectory(projectRoot, project.base.toPath)

    val result = createModuleNode(
      JavaModuleType.getModuleType.getId,
      projectId,
      moduleName,
      moduleFilesDirectory,
      project.base.toPath.toCanonicalPath.toString,
      shouldCreateNestedModule
    )
    result.setInheritProjectCompileOutputPath(false)

    result.add(createLegacyContentRoot(project))

    prefixModuleNameWithGroup(result, moduleGroup)

    val projectDependencies = project.dependencies
    addAllRequiredDataToModuleNode(
      libraryDataByName,
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

  private def createModuleFilesDirectory(projectRoot: Path, moduleBase: Path): String = {
    val relativePath =
      try projectRoot.relativize(moduleBase).toString
      catch
        case _: IllegalArgumentException => ""

    val projectRootDirectory = Seq(projectRoot.getFileName.toString).filter(_.nonEmpty)
    val pathComponents = projectRootDirectory :+ relativePath

    val defaultModuleFilesDir = getDefaultModuleFilesDirectory(projectRoot)
    Path.of(defaultModuleFilesDir, pathComponents*).toCanonicalPath.toString
  }

  private def createModuleWithAllRequiredDataForSeparateProdAndTestSources(
    project: sbtStructure.ProjectData,
    projectRoot: Path,
    moduleName: String,
    moduleGroup: Option[String],
    libraryDataByName: Map[String, esProjectData.LibraryData],
    shouldCreateNestedModule: Boolean,
    sourcesDetails: ProjectSourcesDetails
  )(implicit context: ImportContext): CompleteModuleSourceSet = {
    // TODO use both ID and Name when related flaws in the External System will be fixed
    // TODO explicit canonical path is needed until IDEA-126011 is fixed
    val projectId = ModuleNode.combinedId(project.id, Option(project.buildURI))
    val moduleFilesDirectory = createModuleFilesDirectory(projectRoot, project.base.toPath)
    val parentModule = createModuleNode(
      JavaModuleType.getModuleType.getId,
      projectId,
      moduleName,
      moduleFilesDirectory,
      project.base.toPath.toCanonicalPath.toString,
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
    val contentRootWithProjectBase = (testContentRoots ++ mainContentRoots).find(_.data.getRootPath == SbtUtil.normalizePath(project.base.toPath))
    contentRootWithProjectBase match {
      case Some(contentRoot) => storeExcludedPathsInContentRoot(contentRoot, project)
      case None if sourcesDetails.canCreateParentContentRoot => parentModule.add(createParentContentRoot(project))
      case _ =>
    }

    prodModule.addAll(mainContentRoots)
    testModule.addAll(testContentRoots)

    addAllRequiredDataToModuleNode(
      libraryDataByName,
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
      esProjectData.ExternalSystemSourceType.SOURCE,
      CompileScope,
    )

    addAllRequiredDataToModuleNode(
      libraryDataByName,
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
      esProjectData.ExternalSystemSourceType.TEST,
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
  )(using context: ImportContext): (SbtSourceSetModuleNode, SbtSourceSetModuleNode) = {
    def sbtSourceSetModule(sourceSetName: SourceSetType): SbtSourceSetModuleNode = {
      val moduleId = ModuleNode.combinedId(s"${project.id}:$sourceSetName", Option(project.buildURI))
      val moduleNode = new SbtSourceSetModuleNode(
        JavaModuleType.getModuleType.getId,
        moduleId,
        sourceSetName,
        moduleFilesDirectoryPath,
        project.base.toPath.toCanonicalPath.toString
      )
      moduleNode.setInternalName(s"$moduleNameWithGroup.$sourceSetName")
      moduleNode
    }

    (sbtSourceSetModule(SourceSetType.Main), sbtSourceSetModule(SourceSetType.Test))
  }

  private def setCompileOutputPathsForLegacyModule(
    moduleNode: ModuleDataNodeType,
    configurations: Seq[ConfigurationData],
  )(implicit context: ImportContext): Unit =
    Seq(
      (CompileScope, esProjectData.ExternalSystemSourceType.SOURCE),
      (TestScope, esProjectData.ExternalSystemSourceType.TEST)
    ).foreach { case (scope, sourceType) =>
      setCompileOutputPaths(moduleNode, configurations, sourceType, scope)
    }

  private def setCompileOutputPaths(
    moduleNode: ModuleDataNodeType,
    configurations: Seq[ConfigurationData],
    sourceType: esProjectData.ExternalSystemSourceType,
    scope: String,
  )(implicit context: ImportContext): Unit = {
    def sbtOutputPath(scope: String): Option[String] =
      configurations
        .find(_.id == scope)
        .map(_.classes.toPath.toCanonicalPath.toString)

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
    libraryDataByName: Map[String, esProjectData.LibraryData],
    moduleDependencies: Seq[ModuleDependencyData],
    jarDependencies: Seq[JarDependencyData],
    projectData: ProjectData,
    moduleNode: ModuleDataNodeType,
    moduleType: ModuleType,
    displayName: String,
    projectDependencies: Seq[?]
  )(implicit context: ImportContext): Unit = {
    moduleNode.add(new SbtDisplayModuleNameNode(displayName))

    // in sbt source set modules task/settings/command are not inserted
    // maybe it should be implemented in the future
    if (moduleType == LegacyModuleType) addSbtRelatedData(projectData, moduleNode)

    // create unmanaged dependencies, we need to know how many of them there are, they need to be ordered before
    // the managed dependencies SCL-21852
    val unmanagedDependencies = createUnmanagedDependencies(jarDependencies)(moduleNode, offset = projectDependencies.size)
    val unmanagedSourcesAndDocsLibrary = libraryDataByName.get(Sbt.UnmanagedSourcesAndDocsName)

    val libraryDependenciesNodes = createLibraryDependencies(moduleDependencies)(
      moduleNode,
      libraryDataByName,
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
    moduleNode.add(new SbtModuleNode(SbtModuleData(projectData.id, projectData.buildURI, projectData.base.toPath)))
    moduleNode.addAll(unmanagedDependencies)
    unmanagedSourcesAndDocsLibrary.foreach { lib =>
      val dependency = new LibraryDependencyNode(moduleNode, lib, esProjectData.LibraryLevel.MODULE)
      // Place the unmanagedSourcesAndDocsLibrary below project dependencies and unmanaged dependencies (but before library dependencies)
      dependency.setOrder(projectDependencies.size + unmanagedDependencies.size + 1)
      dependency.setScope(DependencyScope.COMPILE)
      moduleNode.add(dependency)
    }
  }

  private def addAllRequiredDataToParentModuleNode(
    projectData: ProjectData,
    moduleNode: ModuleDataNodeType,
  )(using context: ImportContext): Unit = {
    moduleNode.add(new SbtDisplayModuleNameNode(moduleNode.getModuleName))
    moduleNode.add(new SbtModuleNode(SbtModuleData(projectData.id, projectData.buildURI, projectData.base.toPath)))
    addSbtRelatedData(projectData, moduleNode)

    val data = SbtModuleExtData(
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
    buildBaseDir: Path
  )

  private def createBuildModule(
    build: sbtStructure.BuildData,
    projects: Seq[ProjectData],
    defaultModuleFilesDirectory: String,
    localCachePath: Option[String],
    projectToParentModule: Map[ProjectData, ModuleDataNodeType],
    buildProjectsGroups: Seq[BuildProjectsGroup],
    isPreview: Boolean,
    useShellImport: Boolean
  )(implicit context: ImportContext): BuildModuleNodeWithBuildBaseDir = {

    val buildBaseProject = {
      // Picking the root project from the buildProjectsGroups should be the most appropriate,
      // but, just in case, the old option has also been preserved
      val baseFromGroup = buildProjectsGroups.find(_.buildUri == build.uri).map(_.rootProject)
      baseFromGroup.orElse(
        projects
          .filter(p => p.buildURI == build.uri)
          .foldLeft(None: Option[ProjectData]) {
            case (None, p) => Some(p)
            case (Some(p), p1) =>
              val parent = if (p1.base.toPath.isUnder(p.base.toPath)) p else p1
              Some(parent)
          }
      )
    }

    val buildId = buildBaseProject.flatMap(projectToParentModule.get)
      .map(_.getModuleName + Sbt.BuildModuleSuffix)
      .getOrElse(build.uri.toString)

    val buildBaseDir: Path = buildBaseProject
      .map(_.base.toPath)
      .getOrElse {
        if (build.uri.getScheme == "file") Path.of(build.uri.getPath)
        else projects.head.base.toPath // this really shouldn't happen
      }

    val buildProjectDirRoot = buildBaseDir / Sbt.ProjectDirectory

    val rootNode = buildBaseProject.flatMap(projectToParentModule.get)
    val moduleFilesDirectory = rootNode.map(_.getModuleFileDirectoryPath).getOrElse(defaultModuleFilesDirectory)
    // TODO explicit canonical path is needed until IDEA-126011 is fixed
    val result = createModuleNode(
      SbtModuleType.instance.getId, buildId, buildId, moduleFilesDirectory, buildProjectDirRoot.toCanonicalPath.toString, shouldCreateNestedModule = true
    )

    result.add(new SbtDisplayModuleNameNode(buildId))
    //todo: probably it should depend on sbt version?
    result.add(ModuleSdkNode.inheritFromProject)

    result.setInheritProjectCompileOutputPath(false)
    result.setCompileOutputPath(esProjectData.ExternalSystemSourceType.SOURCE, (buildProjectDirRoot / Sbt.TargetDirectory / "idea-classes").toCanonicalPath.toString)
    result.setCompileOutputPath(esProjectData.ExternalSystemSourceType.TEST, (buildProjectDirRoot / Sbt.TargetDirectory / "idea-test-classes").toCanonicalPath.toString)
    result.add(createBuildContentRoot(buildProjectDirRoot, isPreview))

    val library = {
      def preparePaths(paths: Seq[InterpretablePath]): Seq[String] =
        paths
          .map(_.toPath)
          .filter(_.exists)
          .filter(path =>
            /* When the import happens in the sbt shell or in the sbt process with sbt 1.5.0+, the sbt-structure jar is present on the classpath,
            even though the user hasn't configured it. It's an existing, conceptual problem (SCL-24799).
            The import in the sbt shell and the import in the sbt process with sbt 1.5.0+ are different:
              + in the sbt shell - sbt-structure and sbt-idea-shell plugins are added using the `addSbtPlugin` command.
              Configuring them this way results in a situation where, for example, if the user also adds the sbt-structure plugin with `addSbtPlugin`,
              only a single sbt-structure jar will end up on the classpath. Moreover, if the user configures a higher version of the sbt-structure plugin,
              it takes precedence and appears on the classpath, while the version configured by the Scala plugin is discarded.
              Implementing a logic to ignore jars configured this way is non-trivial. For instance, if the user manually configures the sbt-structure plugin
              with the same version used by the Scala plugin and does not have an internet connection, the only sbt-structure jar available would be
              the one bundled in the Scala plugin. Therefore, we cannot simply ignore sbt-structure jars located in the plugin's repo directory
              when importing this way.
              + in the sbt process with sbt 1.5.0+ - sbt-structure jar is added via the `unmanagedJars` task.
              This way, even if the user adds the `sbt-structure` plugin themselves, there will be two jars on the classpath -
              one bundled in the Scala plugin and another from a different location (this one configured by the user).
              Because of this, we can safely ignore the sbt-structure jar from the plugin's repo directory.
            */
            val isStructureJarInRepoDir = path.startsWith(context.repoDir) && path.nameContains("sbt-structure")
            useShellImport || !isStructureJarInRepoDir
          )
          .map(_.toCanonicalPath.toString)

      createModuleLevelDependency(
        name = Sbt.BuildLibraryPrefix + context.sbtVersion,
        classes = preparePaths(build.classes),
        docs = preparePaths(build.docs),
        sources = preparePaths(build.sources),
        scope = DependencyScope.PROVIDED,
        order = 0
      )(result)
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
   * @param isPreview indicate whether it's a preview import or not. If it's a preview import and `buildProjectDirRoot` doesn't exist,
   *                  it shouldn't be added as a source to the build module content root.
   *                  It's a workaround for [[https://youtrack.jetbrains.com/issue/SCL-24181]]
   */
  private def createBuildContentRoot(buildProjectDirRoot: Path, isPreview: Boolean): ContentRootNode = {
    val result = new ContentRootNode(buildProjectDirRoot)

    // Remove this workaround when https://youtrack.jetbrains.com/issue/IJPL-201546/WorkspaceModel-storage-inconsistency is fixed
    val sourceDirs =
      if (!isPreview || buildProjectDirRoot.exists) Seq(buildProjectDirRoot) // , base << 1
      else Nil

    val excludedDirs = Seq(
      buildProjectDirRoot / Sbt.TargetDirectory,
      buildProjectDirRoot / Sbt.ProjectDirectory / Sbt.TargetDirectory,
    )

    result.storeNioPaths(esProjectData.ExternalSystemSourceType.SOURCE, sourceDirs)
    result.storeNioPaths(esProjectData.ExternalSystemSourceType.EXCLUDED, excludedDirs)

    result
  }

  private def createBuildContentRootForScalaPluginUltimateWorkaround(communityProjectDir: Path): ContentRootNode = {
    val result = new ContentRootNode(communityProjectDir)
    val sourceDirs = Seq(communityProjectDir)
    result.storeNioPaths(esProjectData.ExternalSystemSourceType.SOURCE, sourceDirs)
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
      SystemProperties.getUserHome + "/.ivy2/cache".replace('/', java.io.File.separatorChar)
    }
    new SbtIvyResolver("Local cache", localCachePathFinal, isLocal = true, SbtBundle.message("sbt.local.cache"))
  }

  /**
   * Calculate the offset required for library dependencies.
   * This considers unmanaged dependencies, unmanagedSourcesAndDocsLibrary, and project dependencies,
   * as these are all components that should be placed before library dependencies.
   */
  protected def calculateLibraryDepsOffsetMainTestModules(
    unmanagedDependencies: Seq[?],
    unmanagedSourcesAndDocsLibrary: Option[?],
    projectDependencies: Seq[?]
  ): Int =
    unmanagedDependencies.size + unmanagedSourcesAndDocsLibrary.size + projectDependencies.size + 1

  protected def createLibraryDependencies(dependencies: Seq[sbtStructure.ModuleDependencyData])
                                         (moduleData: esProjectData.ModuleData, libraryDataByName: Map[String, esProjectData.LibraryData], offset: Int, useSeparateProdTestSources: Boolean): Seq[LibraryDependencyNode] = {
    val resolvedDependencies =
      if (!useSeparateProdTestSources) resolveLibraryDependencyConflicts(dependencies)
      else dependencies
    resolvedDependencies.zipWithIndex.map { case (dependency, index) =>
      val name = getNameForLibrary(dependency.id)
      val library = libraryDataByName.getOrElse(name,
        throw new ExternalSystemException("Library not found: " + name))
      val data = new LibraryDependencyNode(moduleData, library, esProjectData.LibraryLevel.PROJECT)
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
                                           (moduleData: esProjectData.ModuleData, offset: Int = 0)(using context: ImportContext): Seq[LibraryDependencyNode] = {
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
      val files = dependency.map(_.file.toPath.toCanonicalPath.toString)
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
  )(moduleData: esProjectData.ModuleData): LibraryDependencyNode = {
    val libraryNode = new LibraryNode(name, resolved = true)
    libraryNode.addPaths(esProjectData.LibraryPathType.BINARY, classes)
    libraryNode.addPaths(esProjectData.LibraryPathType.DOC, docs)
    libraryNode.addPaths(esProjectData.LibraryPathType.SOURCE, sources)

    val result = new LibraryDependencyNode(moduleData, libraryNode, esProjectData.LibraryLevel.MODULE)
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

  /**
   * The fixed numeric suffix added to the project name during the preview import in tests to ensure deterministic names.
   */
  val PreviewImportNumberSuffixInTests = 1234

  case class ImportCancelledException(cause: Throwable) extends Exception(cause)

  //I know that it's a hacky dirty solution, but it's sufficient for now
  //It's hard to access process output from tests, because we use quite high-level project import API in tests
  @TestOnly
  @ApiStatus.Internal
  private var processOutputOfLatestStructureDump: String = ""

  @TestOnly
  @ApiStatus.Internal
  def getProcessOutputOfLatestStructureDump: String =
    processOutputOfLatestStructureDump

  private def setProcessOutputOfLatestStructureDump(processOutput: String): Unit =
    processOutputOfLatestStructureDump = processOutput

  private def withStructureFile[T](action: Path => T): T =
    structureFileForTests match {
      case Some(structureFile) =>
        action(structureFile)
      case None =>
        usingTempFile("sbt-structure", Some(".xml")) { structureFile =>
          action(structureFile)
        }
    }

  @TestOnly
  @ApiStatus.Internal
  private var structureFileForTests: Option[Path] = None

  @TestOnly
  @ApiStatus.Internal
  def setStructureFileForTests(structureFile: Path): Unit =
    structureFileForTests = Some(structureFile)

  @TestOnly
  @ApiStatus.Internal
  def clearStructureFileForTests(): Unit =
    structureFileForTests = None

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
  private case object LegacyModuleType extends ModuleType
  private case object ProdModuleType extends NewModuleType
  private case object TestModuleType extends NewModuleType

  /**
   * Contains some options that are actual and unchanged for the whole import process, for all modules
   */
  private[project] case class ImportContext(
    executionSettings: SbtExecutionSettings,
    eelDescriptor: EelDescriptor,
    timingCollector: Option[SbtImportTimingCollector.TimingCollector]
  ) {
    /**
     * @see [[SbtUtil#getRepoDir]]
     */
    val repoDir: Path = SbtUtil.getRepoDir(eelDescriptor)

    /**
     * The sbt version used for the project import.
     *
     * Initially sourced from `executionSettings.sbtVersion`. If importing via the sbt shell is enabled,
     * this value is updated to reflect the actual version used by the shell session,
     * which may differ from the initial settings if the import was deferred.
     *
     * @see [[org.jetbrains.sbt.project.structure.SbtStructureDump#dumpFromShell]]
     */
    var sbtVersion: SbtVersion = executionSettings.sbtVersion
    def useSeparateProdTestSources: Boolean = executionSettings.separateProdTestSources
    def useSeparateCompilerOutputPaths: Boolean = executionSettings.useSeparateCompilerOutputPaths
  }

  private[project] object ImportContext:
    given (context: ImportContext) => EelDescriptor = context.eelDescriptor
}
