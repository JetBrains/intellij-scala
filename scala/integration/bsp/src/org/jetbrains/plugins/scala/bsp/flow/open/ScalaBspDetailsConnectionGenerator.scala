package org.jetbrains.plugins.scala.bsp.flow.open

import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.bsp.extension.points.BspConnectionDetailsGeneratorExtension
import org.jetbrains.plugins.bsp.flow.open.BuildToolId
import org.jetbrains.plugins.bsp.flow.open.wizard.{ConnectionFileOrNewConnection, ImportProjectWizardStep}
import org.jetbrains.plugins.bsp.protocol.connection.BspConnectionDetailsGenerator
import org.jetbrains.plugins.scala.bsp.config.ScalaPluginConstants
import org.jetbrains.plugins.scala.bsp.{BspFeatureFlags, BspPluginTemplates}

import scala.jdk.CollectionConverters._
import java.io.OutputStream
import java.io.File
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.util
import coursier.core.{Dependency, Module, ModuleName, Organization}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}
import scala.collection.immutable.{HashMap, List}

private object DefaultProjectViewFile {
  val DEFAULT_PROJECT_VIEW_FILE_NAME = "projectview.scalaproject" // TODO: is this the right name?
}

class ScalaBspDetailsConnectionGenerator extends BspConnectionDetailsGeneratorExtension {

  private var projectViewFilePath: Option[Path] = None
  override def id(): String = ScalaPluginConstants.ID
  override def displayName(): String = "Scala"

  override def canGenerateBspConnectionDetailsFile(projectPath: VirtualFile): Boolean =
    projectPath.getChildren.exists(child => ScalaPluginConstants.SUPPORTED_CONFIG_FILE_NAMES.contains(child.getName))

  override def generateBspConnectionDetailsFile(projectPath: VirtualFile, outputStream: OutputStream, project: Project): VirtualFile = {
    initializeProjectViewFile(projectPath)
    executeAndWait(
      calculateInstallerCommand(),
      projectPath,
      outputStream,
      project
    )
    getChild(projectPath, List(".bsp", "sbt.json").asJava) // TODO: is it valid? In bazel it is listOf(".bsp", "bazel.json")
  }

  private def calculateProjectViewFilePath(projectPath: VirtualFile): Path =
    projectPath.toNioPath.toAbsolutePath.resolve(DefaultProjectViewFile.DEFAULT_PROJECT_VIEW_FILE_NAME)

  private def initializeProjectViewFile(projectPath: VirtualFile): Unit = {
    projectViewFilePath = Some(calculateProjectViewFilePath(projectPath))
    setDefaultProjectViewFilePathContentIfNotExists()
  }

  private def setDefaultProjectViewFilePathContentIfNotExists(): Unit = {
    projectViewFilePath match {
      case None => Files.write(projectViewFilePath.get, BspPluginTemplates.defaultScalaProjectViewContent.getBytes, StandardOpenOption.CREATE)
      case Some(_) => ()
    }
  }

  private def calculateInstallerCommand(): java.util.List[String] = {
    val javaExecPath = ExternalCommandUtils.calculateJavaExecPath()
    val neededJars = ExternalCommandUtils.calculateNeededJars(
      org = Organization("org.jetbrains.scala"),
      name = ModuleName("scala-bsp"),
      version = "" // TODO: what version should this be? In bazel it is 3.1.0-20231020-cd64dbb-NIGHTLY
    ).mkString(":")

    (List(
      javaExecPath,
      "-cp",
      neededJars,
      "org.jetbrains.bsp.scala.install.Install"
    ) ++ calculateProjectViewFileInstallerOption()).asJava
  }

  private def calculateProjectViewFileInstallerOption(): List[String] = {
    projectViewFilePath match {
      case None => List()
      case Some(path) => List("-p", path.toString)
    }
  }

  override def calculateImportWizardSteps(path: Path, observableMutableProperty: ObservableMutableProperty[ConnectionFileOrNewConnection]): util.List[ImportProjectWizardStep] = ???

  override def executeAndWait(list: util.List[String], virtualFile: VirtualFile, outputStream: OutputStream, project: Project): Unit = ???

  override def getChild(virtualFile: VirtualFile, list: util.List[String]): VirtualFile = ???
}

private object ExternalCommandUtils {
  def calculateJavaExecPath(): String = {
    val javaHome = System.getProperty("java.home")
    javaHome match {
      case null => throw new IllegalStateException("Java needs to be set up before running the plugin")
      case _ => Paths.get(javaHome, "bin", "java").toString
    }
  }

  def calculateNeededJars(org: Organization, name: ModuleName, version: String): List[String] = {
    val attributes: scala.collection.immutable.Map[String, String] = new HashMap[String, String]()
    val dependencies: List[Dependency] = List(Dependency(Module(org, name, attributes), version))

    val fetchTask = coursier.Fetch().addDependencies(dependencies: _*)
    implicit val executionContext: ExecutionContext = fetchTask.cache.ec
    val future = fetchTask.io.future()
    val futureResult: Seq[File] = Await.result(future, Duration.Inf)

    futureResult.map(_.getCanonicalPath).toList
  }
}
