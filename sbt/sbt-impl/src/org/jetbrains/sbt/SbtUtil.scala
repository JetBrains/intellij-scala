package org.jetbrains.sbt

import com.intellij.execution.configurations.ParametersList
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.{DataNode, Key}
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.{Project, ProjectUtil}
import com.intellij.platform.workspace.storage.{EntityStorage, SymbolicEntityId, WorkspaceEntityWithSymbolicId}
import com.intellij.util.net.{ProxyConfiguration, ProxyCredentialStore, ProxyCredentialStoreKt, ProxySettings, ProxyUtils}
import com.intellij.util.{EnvironmentUtil, SystemProperties}
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.scala.build.BuildReporter
import org.jetbrains.plugins.scala.extensions.RichFile
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.plugins.scala.util.ExternalSystemUtil
import org.jetbrains.sbt.Sbt.SbtModuleChildKeyInstance
import org.jetbrains.sbt.buildinfo.BuildInfo
import org.jetbrains.sbt.project.data.{SbtBuildModuleData, SbtModuleData, SbtProjectData}
import org.jetbrains.sbt.project.settings.SbtExecutionSettings
import org.jetbrains.sbt.project.structure.{JvmOpts, SbtOption, SbtOpts}
import org.jetbrains.sbt.project.{SbtExternalSystemManager, SbtProjectSystem}
import org.jetbrains.sbt.settings.SbtSettings

import java.io.File
import java.net.URI
import java.nio.file.Path
import scala.collection.mutable
import scala.jdk.CollectionConverters.MapHasAsScala
import scala.math.Ordering.Implicits.infixOrderingOps

object SbtUtil {
  private lazy val log: Logger = Logger.getInstance(getClass)

  private object CommandLineOptions {
    val globalPlugins = "sbt.global.plugins"
    val globalBase = "sbt.global.base"
  }

  def isSbtModule(module: Module): Boolean =
    ExternalSystemApiUtil.isExternalSystemAwareModule(SbtProjectSystem.Id, module)

  def isSbtProject(project: Project): Boolean = {
    val settings = sbtSettings(project)
    val linkedSettings = settings.getLinkedProjectsSettings
    !linkedSettings.isEmpty
  }

  def sbtSettings(project: Project): SbtSettings =
    ExternalSystemApiUtil.getSettings(project, SbtProjectSystem.Id).asInstanceOf[SbtSettings]

  /** Directory for global sbt plugins given sbt version */
  @VisibleForTesting
  def globalPluginsDirectory(sbtVersion: SbtVersion): File =
    getFileProperty(CommandLineOptions.globalPlugins).getOrElse {
      val base = globalBase(sbtVersion)
      new File(base, "plugins")
    }

  /** Directory for global sbt plugins from parameters if it is explicitly set,
   * otherwise calculate from sbt version.
   */
  def globalPluginsDirectory(sbtVersion: SbtVersion, parameters: ParametersList): File = {
    val maybeCustomDir = customGlobalPluginsDirectory(parameters)
    maybeCustomDir.getOrElse {
      globalPluginsDirectory(sbtVersion)
    }
  }

  private def customGlobalPluginsDirectory(parameters: ParametersList): Option[File] = {
    val customGlobalPlugins = Option(parameters.getPropertyValue(CommandLineOptions.globalPlugins)).map(new File(_))
    val customGlobalBase = Option(parameters.getPropertyValue(CommandLineOptions.globalBase)).map(new File(_))
    val pluginsUnderCustomGlobalBase = customGlobalBase.map(new File(_, "plugins"))
    customGlobalPlugins.orElse(pluginsUnderCustomGlobalBase)
  }

  /** Base directory for global sbt settings. */
  def globalBase(sbtVersion: SbtVersion): File = {
    val global = getFileProperty(CommandLineOptions.globalBase)
    global.getOrElse(defaultVersionedGlobalBase(sbtVersion))
  }

  private def getFileProperty(name: String): Option[File] = Option(System.getProperty(name)) flatMap { path =>
    if (path.isEmpty) None else Some(new File(path))
  }

  private[sbt] def defaultGlobalBase: File = new File(SystemProperties.getUserHome) / Sbt.Extension

  private def defaultVersionedGlobalBase(sbtVersion: SbtVersion): File = {
    defaultGlobalBase / sbtVersion.binaryVersion.presentation
  }

  def isBuiltWithSeparateModulesForProdTest(project: Project): Boolean = {
    val sbtProjectDataOpt = getSbtProjectData(project)
    sbtProjectDataOpt.exists(_.prodTestSourcesSeparated)
  }

  def isPreview(project: Project, projectPath: String): Boolean = {
    val sbtProjectDataOpt = getSbtProjectData(project, Some(projectPath))
    sbtProjectDataOpt.forall(_.isPreview)
  }

  def getSbtModuleDataNode(module: Module): Option[DataNode[_ <: ModuleData]] = {
    val moduleId = Option(ExternalSystemApiUtil.getExternalProjectId(module))
    moduleId.flatMap { id =>
      val project = module.getProject
      val rootProjectPath = Option(ExternalSystemApiUtil.getExternalRootProjectPath(module))
      ExternalSystemUtil.getModuleDataNode(SbtProjectSystem.Id, project, id, rootProjectPath, Some(SbtModuleChildKeyInstance))
    }
  }

  def structurePluginBinaryVersion(sbtVersion: SbtVersion): Version = {
    if (sbtVersion.isSbt2)
      Version("2")
    else if (sbtVersion >= SbtVersion("1.3.0"))
      Version("1.3")
    else if (sbtVersion.value.major(1) >= Version("1"))
      Version("1.0")
    else
      sbtVersion.value.major(2) //effectively ~ 0.13
  }

  def detectSbtVersion(project: Project): SbtVersion = {
    val workingDirPath = getWorkingDirPath(project)
    val workingDir = new File(workingDirPath)
    val sbtSettings = SbtExternalSystemManager.executionSettingsFor(project, workingDirPath)
    val launcher = SbtUtil.getLauncherJar(sbtSettings)
    SbtUtil.detectSbtVersion(workingDir.toPath, launcher)
  }

  def detectSbtVersion(projectRoot: Path, sbtLauncher: => Path): SbtVersion =
    SbtVersionDetector.detectSbtVersion(projectRoot, sbtLauncher)

  def getSbtModuleData(module: Module): Option[SbtModuleData] = {
    val project = module.getProject
    getSbtModuleData(project, module)
  }

  def getSbtModuleData(project: Project, module: Module): Option[SbtModuleData] = {
    val emptyURI = new URI("")

    val moduleDataSeq = getSbtModuleData(project, module, SbtModuleData.Key)
    moduleDataSeq.find(_.buildURI.uri != emptyURI)
  }

  def getSbtModuleData(project: Project, moduleId: String, rootProjectPath: String): Option[SbtModuleData] = {
    val emptyURI = new URI("")

    val moduleDataSeq = getSbtModuleData(project, moduleId, Some(rootProjectPath), SbtModuleData.Key)
    moduleDataSeq.find(_.buildURI.uri != emptyURI)
  }

  def getBuildModuleData(project: Project, module: Module): Option[SbtBuildModuleData] = {
    val emptyURI = new URI("")

    val moduleDataSeq = getSbtModuleData(project, module, SbtBuildModuleData.Key)
    moduleDataSeq.find(_.buildFor.uri != emptyURI)
  }

  def getSbtModuleData[K](project: Project, module: Module, key: Key[K]): Iterable[K] = {
    val moduleId = ExternalSystemApiUtil.getExternalProjectId(module)
    val rootProjectPath = Option(ExternalSystemApiUtil.getExternalRootProjectPath(module))
    getSbtModuleData(project, moduleId, rootProjectPath, key)
  }

  def getSbtModuleData[K](project: Project, moduleId: String, rootProjectPath: Option[String], key: Key[K]): Iterable[K] = {
    val dataEither = ExternalSystemUtil.getModuleData(SbtProjectSystem.Id, project, moduleId, key, rootProjectPath, Some(SbtModuleChildKeyInstance))
    //TODO: do we need to report the warning to user
    // However there is some code which doesn't expect the data to be present and just checks if it exists
    // So before reporting the warning to user we need to review usage code and decide which code expects
    // the data and which not and then probably split API into two versions: something like "get" and "getOptional"...
    dataEither.getOrElse(Nil)
  }

  case class SbtProjectUriAndId(uri: String, id: String)

  def getSbtProjectUriAndId(module: Module): Option[SbtProjectUriAndId] = {
    val moduleData = getSbtModuleData(module)
    moduleData.map { data =>
      SbtProjectUriAndId(data.buildURI.toString, data.id)
    }
  }

  def makeSbtProjectId(data: SbtModuleData): String = {
    val uri = data.buildURI
    val id = data.id
    s"{$uri}$id"
  }

  private def getLauncherDir: File = getDirInPlugin("launcher")

  def getRepoDir: File = getDirInPlugin("repo")

  def getSbtStructureJar(sbtVersion: SbtVersion): Option[File] = {
    val binVersion = structurePluginBinaryVersion(sbtVersion)
    val structurePath =
      if (binVersion ~= Version("2"))
        Some(BuildInfo.sbtStructurePath_2)
      else if (binVersion ~= Version("1.3"))
        Some(BuildInfo.sbtStructurePath_1_3)
      else if (binVersion ~= Version("1.0"))
        Some(BuildInfo.sbtStructurePath_1_0)
      else if (binVersion ~= Version("0.13"))
        Some(BuildInfo.sbtStructurePath_0_13)
      else
        None

    structurePath.map { relativePath =>
      getRepoDir / relativePath
    }
  }

  def getDefaultLauncher: File = getLauncherDir / "sbt-launch.jar"

  def getLauncherJar(settings: SbtExecutionSettings): Path =
    settings.customLauncher.getOrElse(getDefaultLauncher).toPath

  /** Normalizes pathname so that backslashes don't get interpreted as escape characters in interpolated strings. */
  def normalizePath(file: File): String = file.getAbsolutePath.replace('\\', '/')

  private def pluginBase: File = {
    val file: File = jarWith[this.type].toFile
    val deep = if (file.getName == "classes") 1 else 2
    file << deep
  }

  private def getSbtProjectData(project: Project, rootProjectPath: Option[String] = None): Option[SbtProjectData] = {
    val dataEither = ExternalSystemUtil.getProjectData(SbtProjectSystem.Id, project, SbtProjectData.Key, rootProjectPath)
    dataEither.toSeq.flatten.headOption
  }

  private def getDirInPlugin(dirName: String): File = {
    val res = pluginBase / dirName
    if (!res.exists() && isInTest) {
      val start = jarWith[this.type].toFile.parent
      start.flatMap(findDirInPlugin(_, dirName))
        .getOrElse(throw new RuntimeException(s"could not find dir $dirName at or above ${start.get}"))
    }
    else res
  }

  private def findDirInPlugin(from: File, dirName: String): Option[File] = {
    val dir = from / "target" / "plugin" / "Scala" / dirName
    if (dir.isDirectory) Option(dir)
    else from.parent.flatMap(findDirInPlugin(_, dirName))
  }

  private def isInTest: Boolean = ApplicationManager.getApplication.isUnitTestMode

  def sbtVersionParam(sbtVersion: SbtVersion): String =
    s"-Dsbt.version=$sbtVersion"

  /** It is needed as we want to behave exactly like sbt. Sbt does not take into account options with unbalanced quoted derived from a single line from
   * .jvmopts/.sbtopts file. When options entered in the terminal contains unbalanced quotes it still waits until the user aligns the quotes. Additional we don't take into account
   * those parts of line which are commented out (user can comment the whole line or part of them - everything after # will be discarded, provided that # is not in quotes)
   * */
  def removeCommentedOutPartsAndCheckQuotes(options: String): Option[String] = {
    val quotes = "\"'"
    val quotesStack = mutable.Stack[Char]()
    var firstQuote = 0
    val result = options.foldLeft("") { (acc, char) =>
      if (quotes.contains(char)) {
        if (quotesStack.isEmpty) {
          firstQuote = char
          quotesStack.push(char)
        } else if (char == firstQuote) quotesStack.pop()
      }
      if (char == '#' && quotesStack.isEmpty) return Some(acc)
      else acc :+ char
    }
    if (quotesStack.isEmpty) Some(result) else None
  }

  def collectAllOptionsFromJava(workingDir: File, vmOptionsFromSettings: Seq[String], passParentEnvironment: Boolean, userSetEnv: Map[String, String]): Seq[String] = {
    val java_opts_env = environmentsToUse(passParentEnvironment, userSetEnv).get("JAVA_OPTS")
      .map { options => JvmOpts.processJvmOptions(Seq(options)) }
      .getOrElse(Seq.empty)
    java_opts_env ++ JvmOpts.loadFrom(workingDir) ++ vmOptionsFromSettings
  }

  def collectAllOptionsFromSbt(sbtOptions: Seq[String], directory: File, passParentEnvironment: Boolean, userSetEnv: Map[String, String])
                              (implicit reporter: BuildReporter = null): Seq[SbtOption] = {
    val sbt_opts_env = environmentsToUse(passParentEnvironment, userSetEnv).get("SBT_OPTS")
      .map { options =>
        val combinedOptions = SbtOpts.combineOptionsWithArgs(options)
        SbtOpts.mapOptionsToSbtOptions(combinedOptions, directory.getCanonicalPath)
      }.getOrElse(Seq.empty)
    sbt_opts_env ++ SbtOpts.loadFrom(directory) ++ SbtOpts.mapOptionsToSbtOptions(sbtOptions, directory.getCanonicalPath)
  }

  private def environmentsToUse(passParentEnvironment: Boolean, userSetEnv: Map[String, String]) =
    if (passParentEnvironment) EnvironmentUtil.getEnvironmentMap.asScala ++ userSetEnv else userSetEnv

  /**
   * Appending a special suffix to the module name might be needed when unique module names are generated in
   * [[org.jetbrains.sbt.project.SbtProjectResolver.ModuleUniqueInternalNameGenerator]] and when new modules are being created from <code>SbtNestedModuleData</code>.
   * In the second case, this is necessary when it is detected that the module name is already occupied by another module.
   * It was inspired by [[org.jetbrains.plugins.gradle.service.project.data.GradleSourceSetDataService.findDeduplicatedModuleName]]
   */
  def appendSuffixToModuleName(moduleName: String, inc: Int): String =
    moduleName + "~" + inc

  implicit class EntityStorageOps(storage: EntityStorage) {
    def resolveOpt[T <: WorkspaceEntityWithSymbolicId](id: SymbolicEntityId[T]): Option[T] = Option(storage.resolve(id))
  }

  def getStaticProxyConfigurationJvmOptions: Map[String, String] = {
    val proxyConfiguration = ProxySettings.getInstance().getProxyConfiguration
    val credentialStore = ProxyCredentialStore.getInstance()
    val credentialProvider = ProxyCredentialStoreKt.asProxyCredentialProvider(credentialStore)
    proxyConfiguration match {
      case c: ProxyConfiguration.StaticProxyConfiguration =>
        val stringToString = ProxyUtils.asJvmProperties(c, credentialProvider)
        stringToString.asScala.toMap
      case _ =>
        Map.empty
    }
  }

  def getWorkingDirPath(project: Project): String =
    getWorkingDirPathOpt(project)
      .getOrElse(throw new IllegalStateException(s"no project directory found for project ${project.getName}"))

  /**
   * @note the method can return [[None]] for example in tests,
   *       when the test project doesn't yet have modules and the dir can't be guessed
   */
  def getWorkingDirPathOpt(project: Project): Option[String] = {
    //Fist try to calculate root path based on `getExternalRootProjectPath`
    //When sbt project reference another sbt project via `RootProject` this will correctly find the root project path (see SCL-21143)
    //However, if user manually linked multiple SBT projects via external system tool window (sbt tool window)
    //using "Link sbt Project" button (the one with "plus" icon), it  will randomly choose one of the projects
    // TODO - think about some possibility to allow the user to choose in which project the shell should be fired
    val externalRootProjectPath: Option[String] = {
      val modules = ModuleManager.getInstance(project).getModules.toSeq
      modules.iterator.map(ExternalSystemApiUtil.getExternalRootProjectPath).find(_ != null)
    }
    externalRootProjectPath
      .orElse {
        // externalRootProjectPath can be empty when an IDEA project has not yet been "linked" to an external project.
        // In other words, the project has not yet been imported as a project from some build tool. For example, an
        // sbt project on disk can be opened in IDEA before the Scala plugin is even installed and enabled.
        // After the Scala plugin is installed, the project will initially have an empty `externalRootProjectPath` until
        // it is imported as an sbt project using the external system machinery.
        val message = s"Can't calculate external root project path for project `${project.getName}`, fallback to `ProjectUtil.guessProjectDir`"
        if (!isInTest)
          log.warn(message)
        Option(ProjectUtil.guessProjectDir(project)).map(_.getCanonicalPath)
      }
  }

  /**
   * @return path of the directory containing IntelliJ module files (~ `./.idea/modules`)
   */
  def getDefaultModuleFilesDirectory(projectRoot: File): String =
    (projectRoot / Sbt.ModulesDirectory).path

  // NOTE: "*/*" syntax is deprecated since sbt 1.1 and doesn't work in sbt 2
  def sbtStructureGlobalCommand(command: String, sbtVersion: SbtVersion): String =
    if (SbtVersionCapabilities.isSlashSyntaxSupported(sbtVersion))
      s"Global / $command"
    else
      s"*/*:$command"

  def openSeparateMainTestModulesBlogPost(): Unit =
    BrowserLauncher.getInstance().open("https://blog.jetbrains.com/scala/2024/11/19/new-module-layout-for-sbt/")
}
