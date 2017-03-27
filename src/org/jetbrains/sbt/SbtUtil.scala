package org.jetbrains.sbt

import java.io.{BufferedInputStream, File, FileInputStream}
import java.net.URI
import java.util.Properties
import java.util.jar.JarFile

import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.util.BooleanFunction
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.data.SbtModuleData

/**
  * Created by jast on 2017-02-20.
  */
object SbtUtil {

  /**
    * Compare two version strings.
    * From http://stackoverflow.com/questions/6701948/efficient-way-to-compare-version-strings-in-java
    * @return 0 if equal, <0 if v1 is a lower version than v2, >0 if v1 is a higher version than v2
    */
  def versionCompare(str1: String, str2: String): Int = {
    val vals1 = str1.split("\\.")
    val vals2 = str2.split("\\.")
    var i = 0
    // set index to first non-equal ordinal or length of shortest version string
    while ( {
      i < vals1.length && i < vals2.length && vals1(i) == vals2(i)
    }) i += 1
    // compare first non-equal ordinal number
    if (i < vals1.length && i < vals2.length) {
      val diff = Integer.valueOf(vals1(i)).compareTo(Integer.valueOf(vals2(i)))
      return Integer.signum(diff)
    }
    // the strings are equal or one string is a substring of the other
    // e.g. "1.2.3" = "1.2.3" or "1.2.3" < "1.2.3.4"
    Integer.signum(vals1.length - vals2.length)
  }

  /** Directory for global sbt plugins given sbt version */
  def globalPluginsDirectory(sbtVersion: String): File =
    getFileProperty(globalPluginsProperty).getOrElse {
      val base = globalBase(sbtVersion)
      new File(base, "plugins")
    }

  private val globalPluginsProperty = "sbt.global.plugins"
  private val globalBaseProperty = "sbt.global.base"

  /** Base directory for global sbt settings. */
  def globalBase(version: String): File =
    getFileProperty(globalBaseProperty).getOrElse(defaultVersionedGlobalBase(version))

  private def getFileProperty(name: String): Option[File] = Option(System.getProperty(name)) flatMap { path =>
    if (path.isEmpty) None else Some(new File(path))
  }
  private def fileProperty(name: String): File = new File(System.getProperty(name))
  private def defaultGlobalBase = fileProperty("user.home") / ".sbt"
  private def defaultVersionedGlobalBase(sbtVersion: String): File = defaultGlobalBase / sbtVersion

  def majorVersion(sbtVersion: String): String = numbersOf(sbtVersion).take(2).mkString(".")

  def detectSbtVersion(directory: File, sbtLauncher: => File): String =
    sbtVersionIn(directory)
      .orElse(sbtVersionInBootPropertiesOf(sbtLauncher))
      .orElse(implementationVersionOf(sbtLauncher))
      .getOrElse(Sbt.LatestVersion)

  def numbersOf(version: String): Seq[String] = version.split("\\D").toSeq

  private def implementationVersionOf(jar: File): Option[String] =
    readManifestAttributeFrom(jar, "Implementation-Version")

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

  private def sbtVersionIn(directory: File): Option[String] = {
    val propertiesFile = directory / "project" / "build.properties"
    if (propertiesFile.exists()) readPropertyFrom(propertiesFile, "sbt.version") else None
  }

  private def readPropertyFrom(file: File, name: String): Option[String] = {
    using(new BufferedInputStream(new FileInputStream(file))) { input =>
      val properties = new Properties()
      properties.load(input)
      Option(properties.getProperty(name))
    }
  }

  def getSbtProjectIdSeparated(module: Module): (Option[String], Option[String]) = {
    val project = module.getProject
    val moduleId = ExternalSystemApiUtil.getExternalProjectId(module) // nullable, but that's okay for use in predicate

    // seems hacky. but it seems there isn't yet any better way to get the data for selected module?
    val predicate = new BooleanFunction[DataNode[ModuleData]] {
      override def fun(s: DataNode[ModuleData]): Boolean = s.getData.getId == moduleId
    }

    val emptyURI = new URI("")
    val dataManager = ProjectDataManager.getInstance()

    // TODO instead of silently not running a task, collect failures, report to user
    (for {
      projectInfo <- Option(dataManager.getExternalProjectData(project, SbtProjectSystem.Id, project.getBasePath))
      projectStructure <- Option(projectInfo.getExternalProjectStructure)
      moduleDataNode <- Option(ExternalSystemApiUtil.find(projectStructure, ProjectKeys.MODULE, predicate))
      moduleSbtDataNode <- Option(ExternalSystemApiUtil.find(moduleDataNode, SbtModuleData.Key))
      data = {
        dataManager.ensureTheDataIsReadyToUse(moduleSbtDataNode)
        moduleSbtDataNode.getData
      }
      // buildURI should never be empty for true sbt projects, but filtering here handles synthetic projects
      // created from AAR files. Should implement a more elegant solution for AARs.
      uri <- Option(data.buildURI) if uri != emptyURI
    } yield {
      val id = data.id
      (uri.toString, id)
    }) match {
      case Some((uri, id)) => (Some(uri), Some(id))
      case _ => (None, None)
    }
  }

  def getSbtProjectId(module: Module): Option[String] = getSbtProjectIdSeparated(module) match {
    case (Some(uri), Some(id)) => Some(s"{$uri}$id")
    case _ => None
  }
}
