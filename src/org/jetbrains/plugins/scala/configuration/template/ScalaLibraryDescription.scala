package org.jetbrains.plugins.scala
package configuration
package template

import java.io.File
import java.util.Collections
import javax.swing.JComponent

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.{VfsUtil, VfsUtilCore, VirtualFile}

/**
 * @author Pavel Fatin
 */
object ScalaLibraryDescription extends CustomLibraryDescription {
  def getSuitableLibraryKinds = Collections.singleton(ScalaLibraryKind)

  def createNewLibrary(parentComponent: JComponent, contextDirectory: VirtualFile) = {
    val initialDirectory = guessScalaHome.flatMap(path => Option(VfsUtil.findFileByIoFile(new File(path), false)))

    val virtualFiles = FileChooser.chooseFiles(new ScalaFilesChooserDescriptor(), null, initialDirectory.orNull).toSeq

    val files = virtualFiles.map(VfsUtilCore.virtualToIoFile)

    val allFiles = files.filter(_.isFile) ++ files.flatMap(_.allFiles)

    val components = Component.discoverIn(allFiles)

    if (files.length > 0) ScalaSdkDescriptor.from(components) match {
      case Left(message) => throw new ValidationException(message)
      case Right(sdk) => sdk.createNewLibraryConfiguration()
    } else {
      null
    }
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
            .map(_.replaceFirst( """[/\\]?bin[/\\]?$""", ""))
}