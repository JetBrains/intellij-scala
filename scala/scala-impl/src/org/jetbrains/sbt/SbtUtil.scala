package org.jetbrains.sbt

import java.io.{BufferedInputStream, File, FileInputStream}
import java.net.URI
import java.util.Properties
import java.util.jar.JarFile

import com.intellij.execution.configurations.ParametersList
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.{DataNode, Key, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.util.BooleanFunction
import org.jetbrains.plugins.scala.buildinfo.BuildInfo
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.data.SbtModuleData

import scala.collection.JavaConverters._
/**
  * Created by jast on 2017-02-20.
  */
object SbtUtil {

  object CommandLineOptions {
    val globalPlugins = "sbt.global.plugins"
    val globalBase = "sbt.global.base"
  }

  /** Directory for global sbt plugins given sbt version */
  def globalPluginsDirectory(sbtVersion: Version): File =
    getFileProperty(CommandLineOptions.globalPlugins).getOrElse {
      val base = globalBase(sbtVersion)
      new File(base, "plugins")
    }

  /** Directory for global sbt plugins from parameters if it is explicitly set,
    * otherwise calculate from sbt version.
    */
  def globalPluginsDirectory(sbtVersion: Version, parameters: ParametersList): File = {
    val customGlobalPlugins = Option(parameters.getPropertyValue(CommandLineOptions.globalPlugins)).map(new File(_))
    val customGlobalBase = Option(parameters.getPropertyValue(CommandLineOptions.globalBase)).map(new File(_))
    val pluginsUnderCustomGlobalBase = customGlobalBase.map(new File(_, "plugins"))

    customGlobalPlugins
      .orElse(pluginsUnderCustomGlobalBase)
      .getOrElse(globalPluginsDirectory(sbtVersion))
  }

  /** Base directory for global sbt settings. */
  def globalBase(version: Version): File =
    getFileProperty(CommandLineOptions.globalBase).getOrElse(defaultVersionedGlobalBase(version))


  private def getFileProperty(name: String): Option[File] = Option(System.getProperty(name)) flatMap { path =>
    if (path.isEmpty) None else Some(new File(path))
  }
  private def fileProperty(name: String): File = new File(System.getProperty(name))
  private[sbt] def defaultGlobalBase = fileProperty("user.home") / ".sbt"
  private def defaultVersionedGlobalBase(sbtVersion: Version): File = {
    defaultGlobalBase / binaryVersion(sbtVersion).presentation
  }

  def binaryVersion(sbtVersion: Version): Version =
    // 1.0.0 milestones are regarded as not bincompat by sbt
    if ((sbtVersion ~= Version("1.0.0")) && sbtVersion.presentation.contains("-M"))
      sbtVersion
    // sbt uses binary version x.0 for [x.0,x+1.0[
    else if (sbtVersion.major(1) >= Version("1")) {
      val major = sbtVersion.major(1).presentation
      Version(s"$major.0")
    } else sbtVersion.major(2)

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

  private def readPropertyFrom(file: File, name: String): Option[String] = {
    using(new BufferedInputStream(new FileInputStream(file))) { input =>
      val properties = new Properties()
      properties.load(input)
      Option(properties.getProperty(name))
    }
  }

  def getSbtModuleData(module: Module): Option[SbtModuleData] = {
    val project = module.getProject
    val moduleId = ExternalSystemApiUtil.getExternalProjectId(module) // nullable, but that's okay for use in predicate
    getSbtModuleData(project, moduleId)
  }

  def getSbtModuleData(project: Project, moduleId: String): Option[SbtModuleData] = {
    val emptyURI = new URI("")

    getModuleData(project, moduleId, SbtModuleData.Key)
      .find(_.buildURI != emptyURI)
  }

  def getModuleData[K](project: Project, moduleId: String, key: Key[K]): Iterable[K] = {
    // seems hacky. but apparently there isn't yet any better way to get the data for selected module?
    val predicate = new BooleanFunction[DataNode[ModuleData]] {
      override def fun(s: DataNode[ModuleData]): Boolean = s.getData.getId == moduleId
    }

    val dataManager = ProjectDataManager.getInstance()

    // TODO instead of silently not running a task, collect failures, report to user
    val maybeNodes = for {
      projectInfo <- Option(dataManager.getExternalProjectData(project, SbtProjectSystem.Id, project.getBasePath))
      projectStructure <- Option(projectInfo.getExternalProjectStructure)
      moduleDataNode <- Option(ExternalSystemApiUtil.find(projectStructure, ProjectKeys.MODULE, predicate))
      dataNodes <- Option(ExternalSystemApiUtil.findAll(moduleDataNode, key))
    } yield {
      dataNodes.asScala.map { node =>
        dataManager.ensureTheDataIsReadyToUse(node)
        node.getData
      }
    }

    maybeNodes.getOrElse(Nil)
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

  def getLauncherDir: File =
    getDirInPlugin("launcher")

  def getSbtStructureJar(sbtVersion: Version): Option[File] = {
    val binVersion = binaryVersion(sbtVersion)
    val structurePath =
      if (binVersion ~= Version("0.13"))
        Some(BuildInfo.sbtStructurePath_0_13)
      else if (binVersion ~= Version("1.0"))
        Some(BuildInfo.sbtStructurePath_1_0)
      else None

    structurePath.map { relativePath =>
      getRepoDir / relativePath
    }
  }

  def getRepoDir: File = getDirInPlugin("repo")

  def getDefaultLauncher: File = getLauncherDir / "sbt-launch.jar"

  /** Normalizes pathname so that backslashes don't get interpreted as escape characters in interpolated strings. */
  def normalizePath(file: File): String = file.getAbsolutePath.replace('\\', '/')

  def latestCompatibleVersion(version: Version): Version = {
    val major = version.major(2)

    val latestInSeries =
      if (major.inRange(Version("0.12"), Version("0.13"))) Sbt.Latest_0_12
      else if (major.inRange(Version("0.13"), Version("1.0"))) Sbt.Latest_0_13
      else if (major.inRange(Version("1.0"), Version("2.0"))) Sbt.Latest_1_0
      else Sbt.LatestVersion // needs to be updated for sbt versions >= 2.0

    if (version < latestInSeries) latestInSeries
    else version
  }

  private def pluginBase = {
    val file: File = jarWith[this.type]
    val deep = if (file.getName == "classes") 1 else 2
    file << deep
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
}
