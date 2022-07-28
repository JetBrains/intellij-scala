package org.jetbrains.plugins.scala
package project

import com.intellij.openapi.roots.libraries.LibraryProperties
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import org.jetbrains.plugins.scala.util.HashBuilder._

import java.io.File

final class ScalaLibraryProperties private(
  private[this] var _languageLevel: ScalaLanguageLevel,
  private[this] var _compilerClasspath: Seq[File],
  private[this] var _scaladocExtraClasspath: Seq[File],
) extends LibraryProperties[ScalaLibraryPropertiesState] {
  import ScalaLibraryProperties._

  def languageLevel: ScalaLanguageLevel = _languageLevel

  def languageLevel_=(languageLevel: ScalaLanguageLevel): Unit = {
    if (_languageLevel != languageLevel)
      settings.ScalaCompilerConfiguration.incModificationCount()
    _languageLevel = languageLevel
  }

  def compilerClasspath: Seq[File] = _compilerClasspath
  def scaladocExtraClasspath: Seq[File] = _scaladocExtraClasspath

  def compilerClasspath_=(classpath: Seq[File]): Unit = {
    _compilerClasspath = classpath
  }
  def scaladocExtraClasspath_=(classpath: Seq[File]): Unit = {
    _scaladocExtraClasspath = classpath
  }

  override def loadState(state: ScalaLibraryPropertiesState): Unit = {
    languageLevel = state.getLanguageLevel
    compilerClasspath = state.getCompilerClasspath.map(pathToFile).toSeq
    scaladocExtraClasspath = state.getScaladocExtraClasspath.map(pathToFile).toSeq
  }

  override def getState: ScalaLibraryPropertiesState = new ScalaLibraryPropertiesState(
    languageLevel,
    compilerClasspath.map(fileToPath).toArray,
    scaladocExtraClasspath.map(fileToPath).toArray,
  )

  override def equals(obj: Any): Boolean = obj match {
    case properties: ScalaLibraryProperties =>
      languageLevel == properties.languageLevel &&
        compilerClasspath.map(_.getAbsolutePath) == properties.compilerClasspath.map(_.getAbsolutePath) &&
        scaladocExtraClasspath.map(_.getAbsolutePath) == properties.scaladocExtraClasspath.map(_.getAbsolutePath)
    case _ => false
  }

  override def hashCode: Int = languageLevel #+ compilerClasspath #+ scaladocExtraClasspath

  override def toString = s"ScalaLibraryProperties($languageLevel, $compilerClasspath, $scaladocExtraClasspath)"
}

object ScalaLibraryProperties {

  import ScalaLanguageLevel._
  import VfsUtilCore._

  def apply(): ScalaLibraryProperties =
    apply(None, Seq.empty, Seq.empty)

  def apply(
    version: Option[String],
    compilerClasspath: Seq[File],
    scaladocExtraClasspath: Seq[File]
  ): ScalaLibraryProperties = {
    val languageLevel = version.flatMap(findByVersion).getOrElse(getDefault)
    new ScalaLibraryProperties(languageLevel, compilerClasspath, scaladocExtraClasspath)
  }

  private def pathToFile(url: String) =
    new File(urlToPath(url))

  private def fileToPath(file: File) =
    pathToUrl(FileUtil.toCanonicalPath(file.getAbsolutePath))
}