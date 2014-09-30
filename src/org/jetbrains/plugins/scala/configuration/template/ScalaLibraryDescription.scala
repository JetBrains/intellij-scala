package org.jetbrains.plugins.scala
package configuration
package template

import java.io.File
import java.util.Collections
import javax.swing.JComponent

import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile

import scala.collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
object ScalaLibraryDescription extends CustomLibraryDescription {
  def getSuitableLibraryKinds = Collections.singleton(ScalaLibraryKind)

  def createNewLibrary(parentComponent: JComponent, contextDirectory: VirtualFile) = {
    val sdks = guessScalaHome.toSeq.flatMap(it => sdksIn(new File(it))).map(SdkChoice(_, "System")) ++
            ivySdks.map(SdkChoice(_, "Ivy")) ++ mavenSdks.map(SdkChoice(_, "Maven"))

    val dialog = new SdkSelectionDialog(parentComponent, sdks.asJava)

    val sdk = Option(dialog.open())

    sdk.map(_.createNewLibraryConfiguration()).orNull
  }

//  override def getDefaultLevel = LibrariesContainer.LibraryLevel.GLOBAL // TODO

  // TODO improve, support multiple variants
  def guessScalaHome: Option[String] = {
    val fromHome = env("SCALA_HOME").flatMap(existing)

    val osDefault = if (SystemInfo.isWindows) {
      env("ProgramFiles").flatMap(existing).flatMap(findScalaInDirectory).orElse(
        env("ProgramFiles(x86)").flatMap(existing).flatMap(findScalaInDirectory))
    } else if (SystemInfo.isMac) {
      existing("/opt/").flatMap(findScalaInDirectory)
    } else if (SystemInfo.isLinux) {
      existing("/usr/share/java/")
    } else {
      None
    }

    val fromCommandPath = env("PATH").flatMap(findScalaInCommandPath).flatMap(existing)

    fromHome.orElse(osDefault).orElse(fromCommandPath)
  }

  private def env(name: String): Option[String] = Option(System.getenv(name))

  private def existing(path: String): Option[String] = if (new File(path).exists) Some(path) else None

  private def findScalaInDirectory(directory: String): Option[String] = {
    val subdirectories = new File(directory).listFiles.toSeq
    subdirectories.find(_.getName.toLowerCase.startsWith("scala")).map(_.getPath)
  }

  private def findScalaInCommandPath(path: String): Option[String] =
    path.split(File.pathSeparator)
            .find(_.toLowerCase.contains("scala"))
            .map(_.replaceFirst("""[/\\]?bin[/\\]?$""", ""))

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