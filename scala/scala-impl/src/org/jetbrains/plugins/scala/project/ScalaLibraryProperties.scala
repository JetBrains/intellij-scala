package org.jetbrains.plugins.scala
package project

import java.io.File

import com.intellij.openapi.roots.libraries.LibraryProperties
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import org.jetbrains.plugins.scala.util.HashBuilder._

/**
 * @author Pavel Fatin
 */
final class ScalaLibraryProperties private(private[this] var _languageLevel: ScalaLanguageLevel,
                                           private[this] var _compilerClasspath: Seq[File])
  extends LibraryProperties[ScalaLibraryPropertiesState] {

  import ScalaLibraryProperties._

  def languageLevel: ScalaLanguageLevel = _languageLevel

  def languageLevel_=(languageLevel: ScalaLanguageLevel): Unit = {
    if (_languageLevel != languageLevel)
      settings.ScalaCompilerConfiguration.incModificationCount()
    _languageLevel = languageLevel
  }

  def compilerClasspath: Seq[File] = _compilerClasspath

  def compilerClasspath_=(compilerClasspath: Seq[File]): Unit = {
    _compilerClasspath = compilerClasspath
  }

  override def loadState(state: ScalaLibraryPropertiesState): Unit = {
    languageLevel = state.getLanguageLevel
    compilerClasspath = state.getCompilerClasspath.map(pathToFile)
  }

  override def getState: ScalaLibraryPropertiesState = new ScalaLibraryPropertiesState(
    languageLevel,
    compilerClasspath.map(fileToPath).toArray
  )

  override def equals(obj: Any): Boolean = obj match {
    case properties: ScalaLibraryProperties =>
      languageLevel == properties.languageLevel &&
        compilerClasspath == properties.compilerClasspath
    case _ => false
  }

  override def hashCode: Int = languageLevel #+ compilerClasspath

  override def toString = s"ScalaLibraryProperties($languageLevel, $compilerClasspath)"
}

object ScalaLibraryProperties {

  import ScalaLanguageLevel._
  import VfsUtilCore._

  def apply(version: Option[String] = None,
            compilerClasspath: Seq[File] = Seq.empty): ScalaLibraryProperties = {
    val languageLevel = version.flatMap(findByVersion).getOrElse(getDefault)
    new ScalaLibraryProperties(languageLevel, compilerClasspath)
  }

  private def pathToFile(url: String) =
    new File(urlToPath(url))

  private def fileToPath(file: File) =
    pathToUrl(FileUtil.toCanonicalPath(file.getAbsolutePath))
}