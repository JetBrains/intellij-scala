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
import org.jetbrains.plugins.scala.extensions.RichFile
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.plugins.scala.project.external.{JdkByHome, JdkByName, SdkReference}
import org.jetbrains.plugins.scala.util.ScalaNotificationGroups
import org.jetbrains.sbt.SbtUtil._
import org.jetbrains.sbt.project.SbtProjectResolver._
import org.jetbrains.sbt.project.data._
import org.jetbrains.sbt.project.module.SbtModuleType
import org.jetbrains.sbt.project.settings._
import org.jetbrains.sbt.project.structure.SbtStructureDump.PrintProcessOutputOnFailurePropertyName
import org.jetbrains.sbt.project.structure._
import org.jetbrains.sbt.resolvers.{SbtIvyResolver, SbtMavenResolver, SbtResolver}
import org.jetbrains.sbt.structure.XmlSerializer._
import org.jetbrains.sbt.structure.{BuildData, Configuration, ConfigurationData, DependencyData, DirectoryData, JavaData, ModuleDependencyData, ModuleIdentifier, ProjectData, ScalaData}
import org.jetbrains.sbt.{RichBoolean, Sbt, SbtBundle, SbtUtil, usingTempFile, structure => sbtStructure}

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
class SbtProjectResolver extends ExternalSystemProjectResolver[SbtExecutionSettings] with ExternalSourceRootResolution {

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

    val sbtLauncher = settings.customLauncher.getOrElse(getDefaultLauncher)
    val sbtVersion = detectSbtVersion(projectRoot, sbtLauncher)

    if (isPreview) dummyProject(projectRoot, settings, sbtVersion).toDataNode
    else importProject(taskId, settings, projectRoot, sbtLauncher, sbtVersion, listener)
  }

  private def importProject(
    taskId: ExternalSystemTaskId,
    settings: SbtExecutionSettings,
    projectRoot: File,
    sbtLauncher: File,
    @NonNls sbtVersion: String,
    notifications: ExternalSystemTaskNotificationListener
  ): DataNode[ESProjectData] = {

    @NonNls val importTaskId = s"import:${UUID.randomUUID()}"
    val importTaskDescriptor =
      new TaskOperationDescriptorImpl(SbtBundle.message("sbt.import.to.intellij.project.model"), System.currentTimeMillis(), "project-model-import")

    val esReporter = new ExternalSystemNotificationReporter(projectRoot.getAbsolutePath, taskId, notifications)
    implicit val reporter: BuildReporter = if (isUnitTestMode) {
      val logReporter = new LogReporter
      new CompositeReporter(esReporter, logReporter)
    } else esReporter

    val startTime = System.currentTimeMillis()
    val structureDump = dumpStructure(projectRoot, sbtLauncher, Version(sbtVersion), settings, taskId.findProject())

    // side-effecty status reporting
    structureDump.foreach { _ =>
      val convertStartEvent = new ExternalSystemStartEventImpl(importTaskId, null, importTaskDescriptor)
      val event = new ExternalSystemTaskExecutionEvent(taskId, convertStartEvent)
      notifications.onStatusChange(event)
    }

    val conversionResult: Try[DataNode[ESProjectData]] = structureDump
      .map { case (elem, _) =>
        val data = elem.deserialize[sbtStructure.StructureData].getOrElse(throw new IllegalStateException("Could not deserialize sbt structure data"))
        convert(normalizePath(projectRoot), data, settings.jdk, sbtVersion, settings).toDataNode
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
        new SuccessResultImpl(startTime, endTime, true)
      case Failure(_) =>
        new FailureResultImpl(startTime, endTime, Collections.emptyList[ESFailure]) // TODO error list
    }
    val convertFinishedEvent = new ExternalSystemFinishEventImpl[TaskOperationDescriptor](
      importTaskId, null, importTaskDescriptor, resultNode
    )
    val event = new ExternalSystemTaskExecutionEvent(taskId, convertFinishedEvent)
    notifications.onStatusChange(event)

    conversionResult.get // ok to throw here, that's the way ExternalSystem likes it
  }

  private def dumpStructure(projectRoot: File,
                            sbtLauncher: File,
                            sbtVersion: Version,
                            settings:SbtExecutionSettings,
                            @Nullable project: Project
                           )(implicit reporter: BuildReporter): Try[(Elem, BuildMessages)] = {
    SbtProjectResolver.processOutputOfLatestStructureDump = ""

    val useShellImport = settings.useShellForImport && shellImportSupported(sbtVersion) && project != null
    val options = dumpOptions(settings)

    def doDumpStructure(structureFile: File): Try[(Elem, BuildMessages)] = {
      val structureFilePath = normalizePath(structureFile)

      val dumper = new SbtStructureDump()
      activeProcessDumper = Option(dumper)

      val messageResult: Try[BuildMessages] = {
        if (useShellImport) {
          val messagesF = dumper.dumpFromShell(project, structureFilePath, options, reporter, settings.preferScala2)
          Try(Await.result(messagesF, Duration.Inf)) // TODO some kind of timeout / cancel mechanism
        }
        else {
          val sbtStructureJar = settings
            .customSbtStructureFile
            .orElse(SbtUtil.getSbtStructureJar(sbtVersion))
            .getOrElse(throw new ExternalSystemException(s"Could not find sbt-structure-extractor for sbt version $sbtVersion"))

          log.debug(s"sbtStructureJar: $sbtStructureJar")
          // TODO add error/warning messages during dump, report directly
          val environment = settings.environment ++ settings.userSetEnvironment
          dumper.dumpFromProcess(
            projectRoot, structureFilePath, options,
            settings.vmExecutable, settings.vmOptions, settings.sbtOptions, environment,
            sbtLauncher, sbtStructureJar, settings.preferScala2, settings.passParentEnvironment)
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
          val exceptions = messages.exceptions.map(_.getLocalizedMessage).mkString("\n")
          val errorMsgs = messages.errors.map(_.getMessage).mkString("\n")
          val message = error.getMessage + "\n" +
            exceptions + (if (exceptions.nonEmpty) "\n" else "") +
            errorMsgs
          Failure(new Exception(message, error.getCause))
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
    } else if (!importSupported(sbtVersion)) {
      val message = SbtBundle.message("sbt.sincesbtversion.required", sinceSbtVersion)
      Failure(new UnsupportedOperationException(message))
    }
    else {
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
          doDumpStructure(structureFile)
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

  private def dumpOptions(settings: SbtExecutionSettings): Seq[String] =
    Seq("download") ++
      settings.resolveClassifiers.seq("resolveClassifiers") ++
      settings.resolveSbtClassifiers.seq("resolveSbtClassifiers") ++
      settings.insertProjectTransitiveDependencies.seq("insertProjectTransitiveDependencies")

  /**
   * Create project preview without using sbt, since sbt import can fail and users would have to do a manual edit of the project.
   * Also sbt boot makes the whole process way too slow.
   */
  private def dummyProject(projectRoot: File, settings: SbtExecutionSettings, sbtVersion: String): Node[ESProjectData] = {

    // TODO add default scala sdk and sbt libs (newest versions or so)

    val projectUri = projectRoot.toURI
    val projectPath = projectRoot.getAbsolutePath
    val projectName = normalizeModuleId(projectRoot.getName)
    val projectTmpName = projectName + "_" + Random.nextInt(10000)
    val sourceDir = new File(projectRoot, "src/main/scala")
    val classDir = new File(projectRoot, "target/dummy")

    val dummyConfigurationData = ConfigurationData(CompileScope, Seq(DirectoryData(sourceDir, managed = false)), Seq.empty, Seq.empty, classDir)
    val dummyJavaData = JavaData(None, Seq.empty)
    val dummyDependencyData = DependencyData(Seq.empty, Seq.empty, Seq.empty)
    val dummyRootProject = ProjectData(
      projectTmpName, projectUri, projectTmpName, s"org.$projectName", "0.0", projectRoot, None, Seq.empty,
      new File(projectRoot, "target"), Seq(dummyConfigurationData), Option(dummyJavaData), None, CompileOrder.Mixed.toString,
      dummyDependencyData, Set.empty, None, Seq.empty, Seq.empty, Seq.empty
    )

    val projects = Seq(dummyRootProject)

    val projectNode = new ProjectNode(projectName, projectPath, projectPath)
    val libraryNodes = Seq.empty[LibraryNode]
    val moduleFilesDirectory = new File(projectPath, Sbt.ModulesDirectory)
    val buildProjectsGroup = Seq(BuildProjectsGroup(projectUri, dummyRootProject, projects, projectTmpName))
    val projectToModule = createModules(buildProjectsGroup, libraryNodes, moduleFilesDirectory, insertProjectTransitiveDependencies = false, groupProjectsFromSameBuild = false)

    val dummySbtProjectData = SbtProjectData(settings.jdk.map(JdkByName), sbtVersion, projectPath, projectTransitiveDependenciesUsed = false)
    projectNode.add(new SbtProjectNode(dummySbtProjectData))
    projectNode.addAll(projectToModule.values)

    val dummyBuildData = BuildData(projectUri, Seq.empty, Seq.empty, Seq.empty, Seq.empty)
    val buildModule = createBuildModule(dummyBuildData, projects, moduleFilesDirectory, None, sbtVersion, projectToModule, shouldGroupModules = false)
    projectNode.add(buildModule.moduleNode)

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

  private def convert(
    root: String,
    data: sbtStructure.StructureData,
    settingsJdk: Option[String],
    sbtVersion: String,
    settings: SbtExecutionSettings,
  ): Node[ESProjectData] = {
    val projects: Seq[sbtStructure.ProjectData] = data.projects
    val projectRootFile = new File(root)
    val rootProject: sbtStructure.ProjectData =
      projects.find(p => FileUtil.filesEqual(p.base, projectRootFile))
        .orElse(projects.headOption)
        .getOrElse(throw new RuntimeException("No root project found"))
    val projectNode = new ProjectNode(rootProject.name, root, root)

    val projectJdk = chooseJdk(rootProject, settingsJdk)

    projectNode.add(new SbtProjectNode(SbtProjectData(projectJdk, data.sbtVersion, root, settings.insertProjectTransitiveDependencies)))

    val newPlay2Data = projects.flatMap(p => p.play2.map(d => (p.id, p.base, d)))
    projectNode.add(new Play2ProjectNode(Play2OldStructureAdapter(newPlay2Data)))

    val libraryNodes = createLibraries(data, projects)
    projectNode.addAll(libraryNodes)

    val moduleFilesDirectory = new File(root, Sbt.ModulesDirectory)

    val buildProjectsGroups: Seq[BuildProjectsGroup] =
      createBuildProjectGroups(projects)
    val projectToModule = createModules(buildProjectsGroups, libraryNodes, moduleFilesDirectory, settings.insertProjectTransitiveDependencies, settings.groupProjectsFromSameBuild)

    //Sort modules by id to make project imports more reproducible
    //In particular this will easy testing of `org.jetbrains.sbt.project.SbtProjectImportingTest.testSCL13600`
    //(note, still the order can be different on different machine, because id depends on URI)
    val modulesSorted: Seq[ModuleDataNodeType] = projectToModule.values.toSeq.sortBy(_.getId)
    projectNode.addAll(removeNestedModuleNodes(modulesSorted))

    val shouldGroupModulesFromSameBuild = buildProjectsGroups.size > 1 && settings.groupProjectsFromSameBuild
    val sharedSourceModules = createSharedSourceModules(
      projectToModule,
      libraryNodes,
      moduleFilesDirectory,
      settings.insertProjectTransitiveDependencies,
      shouldGroupModulesFromSameBuild,
      buildProjectsGroups
    )
    if (!shouldGroupModulesFromSameBuild) {
      projectNode.addAll(sharedSourceModules)
    }

    val buildModuleForProject: BuildData => BuildModuleNodeWithBuildBaseDir =
      createBuildModule(_, projects, moduleFilesDirectory, data.localCachePath.map(_.getCanonicalPath), sbtVersion, projectToModule, shouldGroupModulesFromSameBuild)
    val buildModules = data.builds.map(buildModuleForProject)

    if (buildModules.size > 1) {
        buildModules.foreach { buildModule =>
          val ideModuleGroupNameForBuild =
            if (shouldGroupModulesFromSameBuild) {
              val rootNode = findRootNodeForBuild(buildProjectsGroups, buildModule.buildBaseDir, projectToModule)
              rootNode.foreach(_.add(buildModule.moduleNode))
              rootNode.map(_.getInternalName)
            } else
              Some(SbtBuildModulesGroupName)

          val buildModuleInternalName = buildModule.moduleNode.getInternalName
          val moduleInternalNameWithGroup = prependModuleNameWithGroupName(buildModuleInternalName, ideModuleGroupNameForBuild)
          buildModule.moduleNode.setInternalName(moduleInternalNameWithGroup)
        }
    }

    configureBuildModuleDependencies(buildModules)

    projectNode.addAll(removeNestedModuleNodes(buildModules.map(_.moduleNode)))
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

      def addModuleDependency(parentModule: ModuleDataNodeType, childModule: ModuleDataNodeType): Unit = {
        val dependencyNode = new ModuleDependencyNode(parentModule, childModule)
        dependencyNode.setScope(DependencyScope.COMPILE)
        dependencyNode.setExported(true)
        parentModule.add(dependencyNode)
      }

      if (isChild(module1.buildBaseDir.toPath, module2.buildBaseDir.toPath)) {
        addModuleDependency(module2.moduleNode, module1.moduleNode)
      }
      else if (isChild(module2.buildBaseDir.toPath, module1.buildBaseDir.toPath)) {
        addModuleDependency(module1.moduleNode, module2.moduleNode)
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
    val jdkHomeInSbtProject = project.java.flatMap(_.home).map(JdkByHome)

    // default either from project structure or initial import settings
    val default = defaultJdk.map(JdkByName)

    jdkHomeInSbtProject
      .orElse(default)
  }

  private def createModulesDependencies(projectToModule: Map[ProjectData, ModuleDataNodeType], insertProjectTransitiveDependencies: Boolean): Unit = {
    val allModules = projectToModule.values.toSeq
    projectToModule.foreach { case (projectData, moduleNode) =>
      createModuleDependencies(projectData.dependencies.projects, allModules, moduleNode, insertProjectTransitiveDependencies)
    }
  }

  /**
   * The class is designed to generate unique module internal names.
   *
   * We use lowercase version of module internal name to determine uniqueness.
   * This is done because later this name will be used to calculate module file path.
   * And this path will be used to distinguish unique modules later during the import.
   * On some OS (like Mac OS) file paths are case insensitive.
   * When creating a new module in it can skip some module and return an existing one because their path are equal ignoring case
   *
   * Here we always use lower-case version on all OS-ses for easier reproducibility and testing
   * (though technically it's not mandatory to do it on all OS)
   *
   * @see [[com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModifiableModuleModelBridgeImpl.newModule]]
   * @see [[com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModifiableModuleModelBridgeImpl.getModuleByFilePath]]
   */
  private class ModuleUniqueInternalNameGenerator {
    private val reservedNames = new java.util.concurrent.ConcurrentHashMap[String, Int]

    def getUniqueInternalNameAndUpdateRegistry(name: String): String =
      getUniqueInternalNameAndUpdateRegistryOpt(name).getOrElse(name)

    def getUniqueInternalNameAndUpdateRegistryOpt(name: String): Option[String] = {
      val nameLowerCased = name.toLowerCase //using lowercase, see scaladoc
      val numberOfCreatedModulesWithSameName = reservedNames.compute(nameLowerCased, (_, v) => v + 1) - 1
      if (numberOfCreatedModulesWithSameName == 0) //the name is not reserved yet, current name is the first one
        None
      else
        Some(name + numberOfCreatedModulesWithSameName.toString)
    }
  }

  private def createModules(
    projectsGrouped: Seq[BuildProjectsGroup],
    libraryNodes: Seq[LibraryNode],
    moduleFilesDirectory: File,
    insertProjectTransitiveDependencies: Boolean,
    groupProjectsFromSameBuild: Boolean
  ): Map[ProjectData, ModuleDataNodeType] = {
    val librariesData = libraryNodes.map(_.data)
    val unmanagedSourcesAndDocsLibrary = librariesData.find(_.getExternalName == Sbt.UnmanagedSourcesAndDocsName)
    val addAllNecessaryDataToModuleWithLibrariesData =
      addAllRequiredDataToModuleNode(librariesData, unmanagedSourcesAndDocsLibrary)(_: ProjectData, _: ModuleDataNodeType)

    val moduleInternalNameGenerator = new ModuleUniqueInternalNameGenerator()
    val shouldGroupProjectsFromTheSameBuild = projectsGrouped.size > 1 && groupProjectsFromSameBuild
    val projectToModule: Iterable[(ProjectData, ModuleDataNodeType)] = projectsGrouped
      .flatMap { buildProjectsGroup =>
        if (shouldGroupProjectsFromTheSameBuild) {
          //NOTE: when projects are grouped according to their builds is not needed to have single ModuleUniqueInternalNameGenerator for all builds.
          //This is because the uniqueness of project names between all builds is ensured by the unique root project names.
          //From the SBT perspective using ModuleUniqueInternalNameGenerator for non root projects would not be necessary at all (projects id must be unique inside single build),
          //but in IDEA in internal module names all "/" are replaced with "_" and it could happen that in one build the name of one project would be e.g. ro/t
          //and the other one would be ro_t and for SBT uniqueness would be maintained but not for IDEA.
          createModulesWithGroupProjectsFromSameBuild(buildProjectsGroup, moduleFilesDirectory, addAllNecessaryDataToModuleWithLibrariesData)
        } else {
          //NOTE: it is required to have single moduleInternalNameGenerator for all calls of #createModulesWithoutGroupProjectsFromSameBuild, because
          //when grouping via qualified names is done we need to be sure that internal module names which we generate are unique.
          //Proof of this: let's assume that in build.sbt there are references to two others ProjectRef and in both of these projects
          //there is project foo which should be put in a group dummy. Internal names for these projects will be dummy.foo.
          //Because these names are not unique, external system will rename one of them to something different
          //e.g. some_name.dummy.foo (it is done via com.intellij.openapi.externalSystem.service.project.IdeModelsProviderImpl.ModuleNameGenerator.generate),
          //as a result of which they will not be grouped together in the end (the prefix is not the same).
          createModulesWithoutGroupProjectsFromSameBuild(
            buildProjectsGroup, moduleFilesDirectory, Some(moduleInternalNameGenerator), addAllNecessaryDataToModuleWithLibrariesData
          )
        }
      }

    val projectToModuleMap = projectToModule.toMap
    createModulesDependencies(projectToModuleMap, insertProjectTransitiveDependencies)

    projectToModuleMap
  }

  /**
   * In this method the grouping of modules is done in an "old way" - it means that projects belonging to the same build are not grouped together
   * and it may happen that projects from different builds will be grouped together.
   */
  private def createModulesWithoutGroupProjectsFromSameBuild(
    buildProjectsGroup: BuildProjectsGroup,
    moduleFilesDirectory: File,
    moduleInternalNameGeneratorOpt : Option[ModuleUniqueInternalNameGenerator],
    addAllNecessaryDataToModuleNode: (ProjectData, ModuleDataNodeType) => Unit,
  ): Seq[(ProjectData, ModuleDataNodeType)] = {
    val BuildProjectsGroup(_, rootProject, projects, _) = buildProjectsGroup
    val allProjectsInsideBuild = projects :+ rootProject
    val projectNameToProject = allProjectsInsideBuild.groupBy(_.name)

    allProjectsInsideBuild.map { projectData =>
      val (moduleName, moduleGroup) = generateModuleAndGroupName(projectData, None, projectNameToProject)
      val moduleNode =
        createModuleWithAllRequiredData(
          projectData, moduleFilesDirectory, moduleName, moduleGroup , moduleInternalNameGeneratorOpt, addAllNecessaryDataToModuleNode, shouldCreateNestedModule = false
        )
      (projectData, moduleNode)
    }
  }

  /**
   * In this method the grouping of modules is done in such a way that projects that belonging to the same build are grouped together (when there are at least 2 builds).
   * Additionally the root node (displayed in <code>Project Structure | Modules</code>) for projects inside single build is the root project of this build.
   * Because of that, the root project in each build does not participate in the grouping of modules.
   */
  private def createModulesWithGroupProjectsFromSameBuild(
    buildProjectsGroup: BuildProjectsGroup,
    moduleFilesDirectory: File,
    addAllNecessaryDataToModuleNode: (ProjectData, ModuleDataNodeType) => Unit,
  ): Seq[(ProjectData, ModuleDataNodeType)] = {
    val BuildProjectsGroup(_, rootProject, projects, rootProjectModuleNameUnique) = buildProjectsGroup

    //NOTE: it is not needed to pass ModuleUniqueInternalNameGenerator to generate module for the root project,
    //because "rootProjectModuleNameUnique" uniqueness has been ensured in #generateUniqueModuleInternalNameForRootProject
    val rootProjectModule =
      createModuleWithAllRequiredData(
        rootProject, moduleFilesDirectory, rootProjectModuleNameUnique, None, None, addAllNecessaryDataToModuleNode, shouldCreateNestedModule = false
      )

    val rootProjectInternalName = Some(rootProjectModule.getInternalName)
    val projectNameToProject = projects.groupBy(_.name)
    val moduleInternalNameRegistry = new ModuleUniqueInternalNameGenerator
    val projectToModuleForNonRootProjects = projects.map { project =>
      val (moduleName, moduleGroup) = generateModuleAndGroupName(project, rootProjectInternalName, projectNameToProject)
      val module = createModuleWithAllRequiredData(
        project, moduleFilesDirectory, moduleName, moduleGroup, Some(moduleInternalNameRegistry), addAllNecessaryDataToModuleNode, shouldCreateNestedModule = true
      )
      rootProjectModule.add(module)
      (project, module)
    }
    projectToModuleForNonRootProjects :+ (rootProject, rootProjectModule)
  }

  private def generateModuleAndGroupName(
    projectData: ProjectData,
    rootProjectInternalName: Option[String],
    projectNameToProject: Map[String, Seq[ProjectData]],
  ): (String, Option[String]) = {
    val projectName = projectData.name
    val projectsWithSameNameInBuild: Seq[ProjectData] = projectNameToProject.get(projectName).toSeq.flatten

    val nameIsUnique = projectsWithSameNameInBuild.size == 1
    val moduleName =
      if (nameIsUnique) projectName
      else projectData.id

    val groupNameInsideBuild = if (projectsWithSameNameInBuild.size > 1) Seq(projectName) else Nil
    val moduleGroups = (rootProjectInternalName.toSeq ++ groupNameInsideBuild).mkString(".")
    val moduleGroupOpt = if (moduleGroups.isEmpty) None else Some(moduleGroups)
    (moduleName, moduleGroupOpt)
  }

  private def createBuildProjectGroups(projects: Seq[ProjectData]): Seq[BuildProjectsGroup] = {
    val buildToProjects: Map[URI, Seq[ProjectData]] =
      projects.groupBy(_.buildURI)

    //Ensure the group names are the same. There might be collisions if the build use same root project name.
    //This can easily happen if those builds use `val root = project.in(file("."))
    //We reuse ModuleUniqueInternalNameGenerator because the reasoning should be the same for group names as for the module names
    val uniqueNameGenerator = new ModuleUniqueInternalNameGenerator

    //NOTE: sort by URI for a better reproducibility/testability of resulting project structure
    //The matters for unique group names generation
    //(if the order is not specifies, group names of projects with colliding names can have random index suffixes)
    buildToProjects
      .toSeq.sortBy(_._1)
      .map { case (buildUri, projects) =>
        val rootProject = findRootProjectInBuild(projects)

        //NOTE: at this point a unique module name is generated for the root project.
        //So that generating a unique name for the root project is not necessary in
        //org.jetbrains.sbt.project.SbtProjectResolver.createModulesWithGroupProjectsFromSameBuild
        val rootProjectUniqueModuleName = generateUniqueModuleInternalNameForRootProject(rootProject.name, uniqueNameGenerator)
        val projectsWithoutRootProject = projects.filterNot(_ == rootProject)
        BuildProjectsGroup(buildUri, rootProject, projectsWithoutRootProject, rootProjectUniqueModuleName)
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

  protected def createScalaSdkData(scala: Option[ScalaData]): ScalaSdkNode = {
    val data = SbtScalaSdkData(
      scalaVersion = scala.map(_.version),
      scalacClasspath = scala.fold(Seq.empty[File])(_.allCompilerJars),
      scaladocExtraClasspath = scala.fold(Seq.empty[File])(_.extraJars),
      compilerBridgeBinaryJar = scala.flatMap(_.compilerBridgeBinaryJar),
    )
    new ScalaSdkNode(data)
  }

  private def createModuleExtData(project: sbtStructure.ProjectData): ModuleExtNode = {
    val ProjectData(_, _, _, _, _, _, packagePrefix, basePackages, _, _, java, scala, compileOrder, _, _, _, _, _, _) = project

    val data = SbtModuleExtData(
      scalaVersion           = scala.map(_.version),
      scalacClasspath        = scala.fold(Seq.empty[File])(_.allCompilerJars),
      scaladocExtraClasspath = scala.fold(Seq.empty[File])(_.extraJars),
      scalacOptions          = scala.fold(Seq.empty[String])(_.options),
      sdk                    = java.flatMap(_.home).map(JdkByHome),
      javacOptions           = java.fold(Seq.empty[String])(_.options),
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

  private def generateUniqueModuleInternalNameForRootProject(moduleName: String, moduleInternalNameRegistry: ModuleUniqueInternalNameGenerator): String = {
    //NOTE: the idea of replacing all "/" with "_" was taken from com.intellij.openapi.externalSystem.model.project.ModuleData public constructor.
    //It is also done here because that way we are sure the generated internal module name is unique and will not be changed in ModuleData constructor.
    //Replacing all "." with "_" is done to prevent grouping projects from different builds into the same node e.g.
    //let's assume that in build.sbt there are references to two others ProjectRef and in one of these projects the root name is "proj.dummy" and the other
    //is "proj". Such a situation would cause "proj.dummy" to be displayed under "proj" in Project Structure | Modules.
    val moduleInternalName = moduleName.replaceAll("[/\\.]", "_")
    moduleInternalNameRegistry.getUniqueInternalNameAndUpdateRegistry(moduleInternalName)
  }

  private def createModuleWithAllRequiredData(
    project: sbtStructure.ProjectData,
    moduleFilesDirectory: File,
    moduleName: String,
    moduleGroup: Option[String],
    moduleInternalNameRegistry: Option[ModuleUniqueInternalNameGenerator],
    addAllRequiredDataToModuleNode: (ProjectData,ModuleDataNodeType) => Unit,
    shouldCreateNestedModule: Boolean
  ): ModuleDataNodeType = {
    // TODO use both ID and Name when related flaws in the External System will be fixed
    // TODO explicit canonical path is needed until IDEA-126011 is fixed
    val projectId = ModuleNode.combinedId(project.id, Option(project.buildURI))
    //NOTE: module name which is passed in ModuleNode constructor will be saved as external module name, module name and
    //additionally as internal name but with all the "/" characters changed to "_"
    val result = createModuleNode(
      StdModuleTypes.JAVA.getId,
      projectId,
      moduleName,
      moduleFilesDirectory.path,
      project.base.canonicalPath,
      shouldCreateNestedModule
    )
    result.setInheritProjectCompileOutputPath(false)

    val uniqueModuleName = moduleInternalNameRegistry
      .map(_.getUniqueInternalNameAndUpdateRegistry(result.getInternalName))
      .getOrElse(moduleName)

    val moduleInternalNameWithGroup = prependModuleNameWithGroupName(uniqueModuleName, moduleGroup)

    //Using `setInternalName` because there is no way to pass the internal name different than external name and module name via constructor
    result.setInternalName(moduleInternalNameWithGroup)
    //It is required to also update the module name, because this information will be used to know what is the group name
    //for specific module, which is necessary to create shared sources modules (org.jetbrains.sbt.project.ExternalSourceRootResolution.createSourceModuleNodesAndDependencies).
    result.setModuleName(uniqueModuleName)

    addAllRequiredDataToModuleNode(project, result)

    def setCompileOutputPath(scope: String, sourceType: ExternalSystemSourceType): Unit = {
      val configuration = project.configurations.find(_.id == scope)
      configuration.foreach { configuration =>
        result.setCompileOutputPath(sourceType, configuration.classes.path)
      }
    }

    setCompileOutputPath(CompileScope, ExternalSystemSourceType.SOURCE)
    setCompileOutputPath(TestScope, ExternalSystemSourceType.TEST)

    result
  }

  private def addAllRequiredDataToModuleNode(
    librariesData: Seq[LibraryData], unmanagedSourcesAndDocsLibrary: Option[LibraryData]
  )(projectData: ProjectData, moduleNode: ModuleDataNodeType): Unit = {
    val contentRootNode = createContentRoot(projectData)
    moduleNode.add(contentRootNode)
    val libraryDependenciesNodes = createLibraryDependencies(projectData.dependencies.modules)(moduleNode, librariesData)
    moduleNode.addAll(libraryDependenciesNodes)
    moduleNode.add(createModuleExtData(projectData))
    moduleNode.add(createScalaSdkData(projectData.scala))
    moduleNode.add(new SbtModuleNode(SbtModuleData(projectData.id, projectData.buildURI, projectData.base)))
    moduleNode.addAll(createTaskData(projectData))
    moduleNode.addAll(createSettingData(projectData))
    moduleNode.addAll(createCommandData(projectData))
    moduleNode.addAll(createUnmanagedDependencies(projectData.dependencies.jars)(moduleNode))
    unmanagedSourcesAndDocsLibrary.foreach { lib =>
      val dependency = new LibraryDependencyNode(moduleNode, lib, LibraryLevel.MODULE)
      dependency.setScope(DependencyScope.COMPILE)
      moduleNode.add(dependency)
    }
  }

  private def createContentRoot(project: sbtStructure.ProjectData): ContentRootNode = {
    val productionSources = validRootPathsIn(project, CompileScope)(_.sources)
    val productionResources = validRootPathsIn(project, CompileScope)(_.resources)
    val testSources = validRootPathsIn(project, TestScope)(_.sources) ++ validRootPathsIn(project, IntegrationTestScope)(_.sources)
    val testResources = validRootPathsIn(project, TestScope)(_.resources) ++ validRootPathsIn(project, IntegrationTestScope)(_.resources)

    val result = new ContentRootNode(project.base.path)

    result.storePaths(ExternalSystemSourceType.SOURCE, unmanagedDirectories(productionSources))
    result.storePaths(ExternalSystemSourceType.SOURCE_GENERATED, managedDirectories(productionSources))
    result.storePaths(ExternalSystemSourceType.RESOURCE, allDirectories(productionResources))

    result.storePaths(ExternalSystemSourceType.TEST, unmanagedDirectories(testSources))
    result.storePaths(ExternalSystemSourceType.TEST_GENERATED, managedDirectories(testSources))
    result.storePaths(ExternalSystemSourceType.TEST_RESOURCE, allDirectories(testResources))

    val excludedDirs = getExcludedDirs(project)
    excludedDirs.foreach { path =>
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

  private def getExcludedDirs(project: sbtStructure.ProjectData): Seq[File] = {
    val extractedExcludes = project.configurations.flatMap(_.excludes)
    if (extractedExcludes.nonEmpty)
      extractedExcludes.distinct
    else
      Seq(project.target)
  }

  private case class BuildModuleNodeWithBuildBaseDir(
    moduleNode: ModuleDataNodeType,
    buildBaseDir: File
  )

  private def createBuildModule(
    build: sbtStructure.BuildData,
    projects: Seq[ProjectData],
    moduleFilesDirectory: File,
    localCachePath: Option[String],
    sbtVersion: String,
    projectToModule: Map[ProjectData, ModuleDataNodeType],
    shouldGroupModules: Boolean
  ): BuildModuleNodeWithBuildBaseDir = {
    val buildBaseProject =
      projects
        .filter(p => p.buildURI == build.uri)
        .foldLeft(None: Option[ProjectData]) {
          case (None, p) => Some(p)
          case (Some(p), p1) =>
            val parent = if (p.base.isAncestorOf(p1.base)) p else p1
            Some(parent)
        }

    val buildId = buildBaseProject.flatMap(projectToModule.get)
      .map(_.getModuleName + Sbt.BuildModuleSuffix)
      .getOrElse(build.uri.toString)

    val buildBaseDir: File = buildBaseProject
      .map(_.base)
      .getOrElse {
        if (build.uri.getScheme == "file") new File(build.uri.getPath)
        else projects.head.base // this really shouldn't happen
      }

    val buildRoot = buildBaseDir / Sbt.ProjectDirectory

    // TODO explicit canonical path is needed until IDEA-126011 is fixed
    val result = createModuleNode(
      SbtModuleType.instance.getId, buildId, buildId, moduleFilesDirectory.path, buildRoot.canonicalPath, shouldGroupModules
    )

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
      createModuleLevelDependency(Sbt.BuildLibraryPrefix + sbtVersion, classes, docs, sources, DependencyScope.PROVIDED)(result)
    }

    result.add(library)

    result.add(createSbtBuildModuleData(build, projects, localCachePath))

    BuildModuleNodeWithBuildBaseDir(result, buildBaseDir)
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
    val dependenciesWithResolvedConflicts = resolveLibraryDependencyConflicts(dependencies)
    dependenciesWithResolvedConflicts.map { dependency =>
      val name = nameFor(dependency.id)
      val library = libraries.find(_.getExternalName == name).getOrElse(
        throw new ExternalSystemException("Library not found: " + name))
      val data = new LibraryDependencyNode(moduleData, library, LibraryLevel.PROJECT)
      data.setScope(scopeFor(dependency.configurations))
      data
    }
  }

  protected def createUnmanagedDependencies(dependencies: Seq[sbtStructure.JarDependencyData])
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

  private val CompileScope = "compile"
  private val TestScope = "test"
  private val IntegrationTestScope = "it"

  private val SbtBuildModulesGroupName = "sbt-build-modules"

  val IJ_SDK_CLASSIFIERS: Set[String] = Set("IJ-SDK", "IJ-PLUGIN")

  case class ImportCancelledException(cause: Throwable) extends Exception(cause)

  val SBT_PROCESS_CHECK_TIMEOUT_MSEC = 100

  //I know that it's a hacky dirty solution, but it's sufficient for now
  //It's hard to access process output from tests, because we use quite high-level project import API in tests
  @TestOnly
  @ApiStatus.Internal
  var processOutputOfLatestStructureDump: String = ""

  def shellImportSupported(sbtVersion: Version): Boolean =
    sbtVersion >= sinceSbtVersionShell

  def importSupported(sbtVersion: Version): Boolean =
    sbtVersion >= sinceSbtVersion

  // TODO shared code, move to a more suitable object
  val sinceSbtVersion: Version = Version("0.13.0")

  // TODO shared code, move to a more suitable object
  val sinceSbtVersionShell: Version = Version("0.13.5")

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
}
