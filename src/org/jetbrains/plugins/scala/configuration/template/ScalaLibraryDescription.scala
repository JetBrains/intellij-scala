package org.jetbrains.plugins.scala
package configuration
package template

import java.io.File
import java.util.Collections
import javax.swing.JComponent

import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile
import com.intellij.openapi.vfs.VirtualFile

import scala.collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
object ScalaLibraryDescription extends CustomLibraryDescription {
  def getSuitableLibraryKinds = Collections.singleton(ScalaLibraryKind)

  def createNewLibrary(parentComponent: JComponent, contextDirectory: VirtualFile) = {
    implicit val ordering = Version.VersionOrdering.reverse

    val sdks = localSkdsIn(virtualToIoFile(contextDirectory)).map(SdkChoice(_, "Project")) ++
            systemSdks.sortBy(_.version).map(SdkChoice(_, "System")) ++
            ivySdks.sortBy(_.version).map(SdkChoice(_, "Ivy")) ++
            mavenSdks.sortBy(_.version).map(SdkChoice(_, "Maven"))

    val sdk = if (sdks.nonEmpty) {
      val dialog = new SdkSelectionDialog(parentComponent, sdks.asJava)
      Option(dialog.open())
    } else {
      SdkSelection.chooseScalaSdkFiles(parentComponent)
    } 

    sdk.map(_.createNewLibraryConfiguration()).orNull
  }

//  override def getDefaultLevel = LibrariesContainer.LibraryLevel.GLOBAL // TODO

  // TODO sorting

  private def localSkdsIn(directory: File): Seq[ScalaSdkDescriptor] =
    Seq(directory / "lib").flatMap(sdkIn)

  def systemSdks: Seq[ScalaSdkDescriptor] =
    systemScalaRoots.flatMap(path => sdkIn(new File(path)).toSeq)

  private def systemScalaRoots: Seq[String] = {
    val fromApps = systemAppRoots.filter(exists).flatMap(findScalaDirectoriesIn)

    val fromHome = env("SCALA_HOME")

    val fromCommandPath = env("PATH").flatMap(findScalaInCommandPath)

    (fromApps ++ fromHome ++ fromCommandPath).distinct.filter(exists)
  }

  private def systemAppRoots: Seq[String] = if (SystemInfo.isWindows) {
    env("ProgramFiles").toSeq ++ env("ProgramFiles(x86)").toSeq
  } else if (SystemInfo.isMac) {
    Seq("/opt/")
  } else if (SystemInfo.isLinux) {
    Seq("/usr/share/java/")
  } else {
    Seq.empty
  }

  private def env(name: String): Option[String] = Option(System.getenv(name))

  private def exists(path: String): Boolean = new File(path).exists

  private def findScalaDirectoriesIn(directory: String): Seq[String] = {
    val subdirectories = new File(directory).listFiles.toSeq
    subdirectories.filter(_.getName.toLowerCase.startsWith("scala")).map(_.getPath)
  }

  private def findScalaInCommandPath(path: String): Option[String] =
    path.split(File.pathSeparator)
            .find(_.toLowerCase.contains("scala"))
            .map(_.replaceFirst("""[/\\]?bin[/\\]?$""", ""))

  private def sdkIn(root: File): Option[ScalaSdkDescriptor] = {
    val components = Component.discoverIn(root.allFiles)

    ScalaSdkDescriptor.from(components).right.toOption
  }

  def mavenSdks: Seq[ScalaSdkDescriptor] = {
    val root = new File(System.getProperty("user.home")) / ".m2"

    sdksIn(root / "repository" / "org" / "scala-lang")
  }

  def ivySdks: Seq[ScalaSdkDescriptor] = {
    val root = new File(System.getProperty("user.home")) / ".ivy2"

    sdksIn(root / "cache" / "org.scala-lang")
  }

  private def sdksIn(root: File): Seq[ScalaSdkDescriptor] = {
    val components = Component.discoverIn(root.allFiles)

    components.groupBy(_.version).mapValues(ScalaSdkDescriptor.from).toSeq.collect {
      case (Some(version), Right(sdk)) => sdk
    }
  }
}

case class SdkChoice(sdk: ScalaSdkDescriptor, source: String)