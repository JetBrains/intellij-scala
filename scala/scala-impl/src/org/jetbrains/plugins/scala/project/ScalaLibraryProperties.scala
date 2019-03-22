package org.jetbrains.plugins.scala
package project

import java.io.File
import java.util.Objects

import com.intellij.openapi.roots.libraries.LibraryProperties
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore

/**
 * @author Pavel Fatin
 */
final class ScalaLibraryProperties private(private[this] var _languageLevel: ScalaLanguageLevel,
                                           private[this] var _compilerClasspath: Seq[File])
  extends LibraryProperties[ScalaLibraryPropertiesState] {

  import ScalaLibraryProperties._

  def languageLevel: ScalaLanguageLevel = _languageLevel

  def languageLevel_=(languageLevel: ScalaLanguageLevel): Unit = {
    if (_languageLevel != languageLevel) settings.ScalaCompilerConfiguration.incModificationCount()
    _languageLevel = languageLevel
  }

  def compilerClasspath: Seq[File] = _compilerClasspath

  def compilerClasspath_=(compilerClasspath: Seq[File]): Unit = {
    _compilerClasspath = compilerClasspath
  }

  def loadState(state: ScalaLibraryPropertiesState) {
    languageLevel = state.getLanguageLevel
    compilerClasspath = state.getCompilerClasspath.map(pathToFile)
  }

  def getState: ScalaLibraryPropertiesState = new ScalaLibraryPropertiesState(
    languageLevel,
    compilerClasspath.map(fileToPath).toArray
  )

  override def equals(obj: Any): Boolean = obj match {
    case properties: ScalaLibraryProperties =>
      languageLevel == properties.languageLevel &&
        compilerClasspath == properties.compilerClasspath
    case _ => false
  }

  override def hashCode: Int = Objects.hash(languageLevel, compilerClasspath)

  override def toString = s"ScalaLibraryProperties($languageLevel, $compilerClasspath)"
}

object ScalaLibraryProperties {

  import VfsUtilCore._

  def apply(state: ScalaLibraryPropertiesState = new ScalaLibraryPropertiesState()): ScalaLibraryProperties = {
    val properties = new ScalaLibraryProperties(
      null,
      Seq.empty
    )
    properties.loadState(state)
    properties
  }

  def apply(languageLevel: ScalaLanguageLevel,
            compilerClasspath: Seq[File]) = new ScalaLibraryProperties(
    languageLevel,
    compilerClasspath
  )

  def unapply(libraryProperties: LibraryProperties[_]): Option[(ScalaLanguageLevel, Seq[File])] =
    libraryProperties match {
      case properties: ScalaLibraryProperties => Some(properties.languageLevel, properties.compilerClasspath)
      case _ => None
    }

  private def pathToFile(url: String) =
    new File(urlToPath(url))

  private def fileToPath(file: File) =
    pathToUrl(FileUtil.toCanonicalPath(file.getAbsolutePath))
}