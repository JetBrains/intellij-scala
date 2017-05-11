package org.jetbrains.sbt

import java.io.{BufferedInputStream, File, FileInputStream}
import java.net.URI
import java.util.Properties
import java.util.jar.JarFile

import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.util.BooleanFunction
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.data.SbtModuleData

/**
  * Created by jast on 2017-02-20.
  */
object SbtUtil {

  /** Directory for global sbt plugins given sbt version */
  def globalPluginsDirectory(sbtVersion: Version): File =
    getFileProperty(globalPluginsProperty).getOrElse {
      val base = globalBase(sbtVersion.presentation)
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

  def majorVersion(sbtVersion: Version): Version = sbtVersion.major(2)

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

  def getSbtModuleData(module: Module): Option[SbtModuleData] = {
    val project = module.getProject
    val moduleId = ExternalSystemApiUtil.getExternalProjectId(module) // nullable, but that's okay for use in predicate
    getSbtModuleData(project, moduleId)
  }

  def getSbtModuleData(project: Project, moduleId: String): Option[SbtModuleData] = {

    // seems hacky. but it seems there isn't yet any better way to get the data for selected module?
    val predicate = new BooleanFunction[DataNode[ModuleData]] {
      override def fun(s: DataNode[ModuleData]): Boolean = s.getData.getId == moduleId
    }

    val emptyURI = new URI("")
    val dataManager = ProjectDataManager.getInstance()

    // TODO instead of silently not running a task, collect failures, report to user
    for {
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
      if data.buildURI != emptyURI
    } yield {
      data
    }
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
}
