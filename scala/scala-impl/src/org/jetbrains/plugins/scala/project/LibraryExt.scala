package org.jetbrains.plugins.scala.project

import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.project.LibraryExt.{ScalaLibraryKindId, guessLibraryVersionFromName, runtimeVersion}

import java.net.{URI, URL}

// The Scala SDK checks need the library implementation (`LibraryEx`), so they are not part of the project package object.

implicit class LibraryExt(private val library: Library) extends AnyVal with LibraryBase {

  import LibraryExt.*

  override def isScalaSdk: Boolean = library match {
    case libraryEx: LibraryEx => libraryEx.isScalaSdk
    case _ => false
  }

  override def name: Option[String] = Option(library.getName)

  def hasRuntimeLibrary: Boolean = name.exists(isRuntimeLibrary)

  def jarUrls: Set[URL] =
    library
      .getFiles(OrderRootType.CLASSES)
      .map(_.getPath)
      .map(path => new URI(s"jar:file://$path").toURL)
      .toSet
}

object LibraryExt {

  @TestOnly
  def guessLibraryVersionFromName(libraryName: String): Option[String] =
    LibraryVersion.findFirstIn(libraryName)

  /**
   * Examples (see tests for more examples):
   *  - anything-here-1.22.3
   *  - anything-here:1.22.3
   *  - anything-here_1.22.3
   *  - anything-here-1.22.3-bin-db-2-fd41f6b
   */
  private val LibraryVersion = """(?<=[:_\-])\d+\.\d+\.\d+[^:\s]*""".r

  private val RuntimeLibrary = "((?:scala|dotty|scala3)-library).+".r

  private val JarVersion = "(?<=-)\\d+\\.\\d+\\.\\d+\\S*(?=\\.jar$)".r

  def isRuntimeLibrary(name: String): Boolean = RuntimeLibrary.findFirstIn(name).isDefined

  def runtimeVersion(input: String): Option[String] = JarVersion.findFirstIn(input)

  /** The id of [[ScalaLibraryType.Kind]] */
  private[project] val ScalaLibraryKindId = "Scala"
}

implicit class LibraryExExt(private val library: LibraryEx) extends AnyVal {

  // compares the kind id, so that this doesn't load ScalaLibraryType (library editor UI)
  def isScalaSdk: Boolean = {
    val kind = library.getKind
    kind != null && kind.getKindId == ScalaLibraryKindId
  }

  def properties: ScalaLibraryProperties = library.getProperties match {
    case properties: ScalaLibraryProperties => properties
    case _ => throw new IllegalStateException("Library is not a Scala SDK: " + library.getName)
  }
}

trait LibraryBase extends Any {
  def isScalaSdk: Boolean
  def name: Option[String]
  def libraryVersion: Option[String] = name.flatMap(guessLibraryVersionFromName)
  def jarLibraryVersion: Option[String] = name.flatMap(runtimeVersion)
}
