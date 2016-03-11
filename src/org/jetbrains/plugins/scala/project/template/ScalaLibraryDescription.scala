package org.jetbrains.plugins.scala
package project
package template

import java.io.File
import java.util
import java.util.Collections
import javax.swing.JComponent

import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile
import com.intellij.openapi.vfs.VirtualFile

import scala.collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
object ScalaLibraryDescription extends ScalaLibraryDescription {
  override protected val libraryKind = ScalaLibraryKind

  override protected val sdkDescriptor = ScalaSdkDescriptor

  override def dialog(parentComponent: JComponent, provider: () => util.List[SdkChoice]) = {
    new SdkSelectionDialog(parentComponent, provider)
  }

  override def sdks(contextDirectory: VirtualFile) = super.sdks(contextDirectory) ++
    systemSdks.sortBy(_.version).map(SdkChoice(_, "System"))

  override def getDefaultLevel = LibrariesContainer.LibraryLevel.GLOBAL

  private def systemSdks: Seq[SdkDescriptor] =
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
}

trait ScalaLibraryDescription extends CustomLibraryDescription {
  protected val libraryKind: PersistentLibraryKind[ScalaLibraryProperties]

  protected val sdkDescriptor: SdkDescriptorCompanion

  private val userHome = new File(System.getProperty("user.home"))

  protected val ivyRepository = userHome / ".ivy2" / "cache"

  protected val ivyScalaRoot = ivyRepository / "org.scala-lang"

  protected val mavenRepository = userHome / ".m2" / "repository"

  protected val mavenScalaRoot = mavenRepository / "org" / "scala-lang"

  def dialog(parentComponent: JComponent, provide: () => java.util.List[SdkChoice]): SdkSelectionDialog

  def sdks(contextDirectory: VirtualFile): Seq[SdkChoice] = {
    val localSdks = Option(contextDirectory).toSeq.map(cDir => virtualToIoFile(contextDirectory) / "lib").flatMap(sdkIn)
    localSdks.map(SdkChoice(_, "Project")) ++
      ivySdks.sortBy(_.version).map(SdkChoice(_, "Ivy")) ++
      mavenSdks.sortBy(_.version).map(SdkChoice(_, "Maven"))
  }

  def getSuitableLibraryKinds = Collections.singleton(libraryKind)

  def createNewLibrary(parentComponent: JComponent, contextDirectory: VirtualFile) = {
    implicit val ordering = implicitly[Ordering[Version]].reverse
    Option(dialog(parentComponent, () => sdks(contextDirectory).asJava).open())
      .map(_.createNewLibraryConfiguration())
      .orNull
  }

  protected def discoverComponents(root: File) = Component.discoverIn(root.allFiles)

  protected def sdkIn(root: File) = sdkDescriptor.from(discoverComponents(root)).right.toOption

  protected def ivySdks = sdksIn(ivyScalaRoot)

  protected def mavenSdks = sdksIn(mavenScalaRoot)

  private def sdksIn(root: File): Seq[SdkDescriptor] = {
    discoverComponents(root).groupBy(_.version).mapValues(sdkDescriptor.from).toSeq.collect {
      case (Some(version), Right(sdk)) => sdk
    }
  }
}

case class SdkChoice(sdk: SdkDescriptor, source: String)