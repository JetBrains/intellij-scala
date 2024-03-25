package org.jetbrains.sbt

import com.intellij.entities.SbtModuleEntity
import com.intellij.execution.configurations.ParametersList
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.{ModuleEntity, ModuleId}
import com.intellij.platform.workspace.storage.{EntityStorage, SymbolicEntityId, WorkspaceEntityWithSymbolicId}
import com.intellij.util.{EnvironmentUtil, SystemProperties}
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.scala.build.BuildReporter
import org.jetbrains.plugins.scala.extensions.RichFile
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.plugins.scala.util.ExternalSystemUtil
import org.jetbrains.sbt.Sbt.SbtModuleChildKeyInstance
import org.jetbrains.sbt.buildinfo.BuildInfo
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.data.{SbtBuildModuleData, SbtModuleData, SbtProjectData}
import org.jetbrains.sbt.project.structure.{JvmOpts, SbtOption, SbtOpts}
import org.jetbrains.sbt.settings.SbtSettings

import java.io.{BufferedInputStream, File, FileInputStream}
import java.net.URI
import java.util.Properties
import java.util.jar.JarFile
import scala.collection.mutable
import scala.jdk.CollectionConverters.{IteratorHasAsScala, MapHasAsScala}
import scala.util.Using

object SbtUtil {

  object CommandLineOptions {
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
  def globalPluginsDirectory(sbtVersion: Version): File =
    getFileProperty(CommandLineOptions.globalPlugins).getOrElse {
      val base = globalBase(sbtVersion)
      new File(base, "plugins")
    }

  /** Directory for global sbt plugins from parameters if it is explicitly set,
    * otherwise calculate from sbt version.
    */
  def globalPluginsDirectory(sbtVersion: Version, parameters: ParametersList): File = {
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
  def globalBase(version: Version): File =
    getFileProperty(CommandLineOptions.globalBase).getOrElse(defaultVersionedGlobalBase(version))

  private def getFileProperty(name: String): Option[File] = Option(System.getProperty(name)) flatMap { path =>
    if (path.isEmpty) None else Some(new File(path))
  }
  private[sbt] def defaultGlobalBase: File = new File(SystemProperties.getUserHome) / Sbt.Extension
  private def defaultVersionedGlobalBase(sbtVersion: Version): File = {
    defaultGlobalBase / binaryVersion(sbtVersion).presentation
  }

  /**
   * @return - 0.13 for all 0.13.x versions<br>
   *         - 1.0 for all 1.x.y versions<br>
   *         - 2.0 for all 2.x.y versions
   */
  def binaryVersion(sbtVersion: Version): Version = {
    // 1.0.0 milestones are regarded as not bincompat by sbt
    if ((sbtVersion ~= Version("1.0.0")) && sbtVersion.presentation.contains("-M"))
      sbtVersion
    // sbt uses binary version x.0 for [x.0,x+1.0[
    else if (sbtVersion.major(1) >= Version("1")) {
      val major = sbtVersion.major(1).presentation
      Version(s"$major.0")
    } else sbtVersion.major(2)
  }

  def structurePluginBinaryVersion(sbtVersion: Version): Version =
    if (sbtVersion >= Version("1.3.0"))
      Version(s"1.3")
    else if (sbtVersion.major(1) >= Version("1"))
      Version(s"1.2")
    else
      sbtVersion.major(2)

  def detectSbtVersion(directory: File, sbtLauncher: => File): String =
    sbtVersionIn(directory)
      .orElse(sbtVersionInBootPropertiesOf(sbtLauncher))
      .orElse(readManifestAttributeFrom(sbtLauncher, "Implementation-Version"))
      .getOrElse(BuildInfo.sbtLatestVersion)

  def numbersOf(version: String): Seq[String] = version.split("\\D").toSeq

  private def readManifestAttributeFrom(file: File, name: String): Option[String] = {
    val jar = new JarFile(file)
    try {
      Option(jar.getJarEntry("META-INF/MANIFEST.MF")).flatMap { entry =>
        val input = new BufferedInputStream(jar.getInputStream(entry))
        val manifest = new java.util.jar.Manifest(input)
        val attributes = manifest.getMainAttributes
        Option(attributes.getValue(name))
      }
    }
    finally {
      jar.close()
    }
  }

  private def sbtVersionInBootPropertiesOf(jar: File): Option[String] = {
    val appProperties = readSectionFromBootPropertiesOf(jar, sectionName = "app")
    for {
      name <- appProperties.get("name")
      if name == "sbt"
      versionStr <- appProperties.get("version")
      version <- "\\d+(\\.\\d+)+".r.findFirstIn(versionStr)
    } yield version
  }

  private def readSectionFromBootPropertiesOf(launcherFile: File, sectionName: String): Map[String, String] = {
    val Property = "^\\s*(\\w+)\\s*:(.+)".r.unanchored

    def findProperty(line: String): Option[(String, String)] = {
      line match {
        case Property(name, value) => Some((name, value.trim))
        case _ => None
      }
    }

    val jar = new JarFile(launcherFile)
    try {
      Option(jar.getEntry("sbt/sbt.boot.properties")).fold(Map.empty[String, String]) { entry =>
        val lines = scala.io.Source.fromInputStream(jar.getInputStream(entry)).getLines()
        val sectionLines = lines
          .dropWhile(_.trim != s"[$sectionName]").drop(1)
          .takeWhile(!_.trim.startsWith("["))
        sectionLines.flatMap(findProperty).toMap
      }
    } finally {
      jar.close()
    }
  }

  def sbtBuildPropertiesFile(base: File): File =
    base / Sbt.ProjectDirectory / Sbt.PropertiesFile

  private def sbtVersionIn(directory: File): Option[String] =
    sbtBuildPropertiesFile(directory) match {
      case propertiesFile if propertiesFile.exists => readPropertyFrom(propertiesFile, "sbt.version")
      case _ => None
    }

  private def readPropertyFrom(file: File, name: String): Option[String] =
    Using.resource(new BufferedInputStream(new FileInputStream(file))) { input =>
      val properties = new Properties()
      properties.load(input)
      Option(properties.getProperty(name))
    }

  def isBuiltWithProjectTransitiveDependencies(project: Project): Boolean =
    extractSbtProjectData(project, _.projectTransitiveDependenciesUsed)

  def isBuiltWithProdTestSourcesSeparated(project: Project): Boolean =
    extractSbtProjectData(project, _.prodTestSourcesSeparated)

  private def extractSbtProjectData(project: Project, extractor: SbtProjectData => Boolean): Boolean = {
    val sbtProjectDataOpt = SbtUtil.getSbtProjectData(project)
    sbtProjectDataOpt.exists(extractor)
  }

  def getSbtModuleEntity(module: Module): Option[SbtModuleEntity] = {
    val project = module.getProject
    val storage = WorkspaceModel.getInstance(project).getCurrentSnapshot
    val moduleEntityOpt = storage.resolveOpt(new ModuleId(module.getName))
    moduleEntityOpt.flatMap(findSbtModuleEntityForModuleEntity(_, storage))
  }

  def findSbtModuleEntityForModuleEntity(moduleEntity: ModuleEntity, storage: EntityStorage): Option[SbtModuleEntity] = {
    val entities = storage.entities(classOf[SbtModuleEntity]).iterator().asScala.toList
    entities.find(_.getModule == moduleEntity)
  }

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

  def getSbtProjectIdSeparated(module: Module): (Option[String], Option[String]) =
    getSbtModuleData(module) match {
      case Some(data) => (Some(data.buildURI.toString), Some(data.id))
      case _ => (None, None)
    }

  def makeSbtProjectId(data: SbtModuleData): String = {
    val uri = data.buildURI
    val id = data.id
    s"{$uri}$id"
  }

  def getLauncherDir: File = getDirInPlugin("launcher")

  def getLibDir: File = getDirInPlugin("lib")

  def getRepoDir: File = getDirInPlugin("repo")

  def getSbtStructureJar(sbtVersion: Version): Option[File] = {
    val binVersion = structurePluginBinaryVersion(sbtVersion)
    val structurePath =
      if (binVersion ~= Version("0.13"))
        Some(BuildInfo.sbtStructurePath_0_13)
      else if (binVersion ~= Version("1.3"))
        Some(BuildInfo.sbtStructurePath_1_3)
      else if (binVersion > Version("1.0"))
        Some(BuildInfo.sbtStructurePath_1_2)
      else None

    structurePath.map { relativePath =>
      getRepoDir / relativePath
    }
  }

  def getDefaultLauncher: File = getLauncherDir / "sbt-launch.jar"

  /** Normalizes pathname so that backslashes don't get interpreted as escape characters in interpolated strings. */
  def normalizePath(file: File): String = file.getAbsolutePath.replace('\\', '/')

  def latestCompatibleVersion(version: Version): Version = {
    val major = version.major(2)

    val latestInSeries =
      if (major.inRange(Version("0.13"), Version("1.0"))) Sbt.Latest_0_13
      else if (major.inRange(Version("1.0"), Version("2.0"))) Sbt.Latest_1_0
      else Sbt.LatestVersion // needs to be updated for sbt versions >= 2.0

    if (version < latestInSeries) latestInSeries
    else version
  }

  private def pluginBase: File = {
    val file: File = jarWith[this.type]
    val deep = if (file.getName == "classes") 1 else 2
    file << deep
  }

  private def getSbtProjectData(project: Project): Option[SbtProjectData] = {
    val dataEither = ExternalSystemUtil.getProjectData(SbtProjectSystem.Id, project, SbtProjectData.Key)
    dataEither.toSeq.flatten.headOption
  }

  private def getDirInPlugin(dirName: String): File = {
    val res = pluginBase / dirName
    if (!res.exists() && isInTest) {
      val start = jarWith[this.type].parent
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


  def canUpgradeSbtVersion(sbtVersion: Version): Boolean =
    sbtVersion >= MayUpgradeSbtVersion &&
      sbtVersion < SbtUtil.latestCompatibleVersion(sbtVersion)

  def upgradedSbtVersion(sbtVersion: Version): Version =
    if (canUpgradeSbtVersion(sbtVersion))
      SbtUtil.latestCompatibleVersion(sbtVersion)
    else sbtVersion

  def sbtVersionParam(sbtVersion: Version): String =
    s"-Dsbt.version=$sbtVersion"

  def isAddPluginCommandSupported(sbtVersion: Version): Boolean =
    sbtVersion >= AddPluginCommandVersion_1 ||
      sbtVersion.inRange(AddPluginCommandVersion_013, Version("1.0.0"))

  /** Since version 1.2.0 sbt supports injecting additional plugins to the sbt shell with a command.
   * This allows injecting plugins without messing with the user's global directory.
   * https://github.com/sbt/sbt/pull/4211
   */
  private val AddPluginCommandVersion_1 = Version("1.2.0")
  private val AddPluginCommandVersion_013 = Version("0.13.18")

  /** Minimum project sbt version that is allowed version override. */
  private val MayUpgradeSbtVersion = Version("0.13.0")

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

}
