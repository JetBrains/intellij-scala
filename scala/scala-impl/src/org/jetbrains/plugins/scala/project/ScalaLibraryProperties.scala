package org.jetbrains.plugins.scala.project

import com.intellij.openapi.roots.libraries.LibraryProperties
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import org.jetbrains.plugins.scala.util.HashBuilder._

import java.io.File

final class ScalaLibraryProperties private(
  private[this] var _languageLevel: ScalaLanguageLevel,
  private[this] var _compilerClasspath: Seq[File],
  private[this] var _scaladocExtraClasspath: Seq[File],
  private[this] var _compilerBridgeBinaryJar: Option[File]
) extends LibraryProperties[ScalaLibraryPropertiesState] {
  import ScalaLibraryProperties._

  def compilerBridgeBinaryJar: Option[File] = _compilerBridgeBinaryJar
  def compilerBridgeBinaryJar_=(value: Option[File]): Unit = _compilerBridgeBinaryJar = value

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
    compilerClasspath = state.getCompilerClasspath.map(urlToFile).toSeq
    scaladocExtraClasspath = state.getScaladocExtraClasspath.map(urlToFile).toSeq
    compilerBridgeBinaryJar = Option(state.getCompilerBridgeBinaryJar).map(urlToFile)
  }

  override def getState: ScalaLibraryPropertiesState = new ScalaLibraryPropertiesState(
    languageLevel,
    compilerClasspath.map(fileToUrl).toArray,
    scaladocExtraClasspath.map(fileToUrl).toArray,
    compilerBridgeBinaryJar.map(fileToUrl).orNull
  )

  override def equals(obj: Any): Boolean = obj match {
    case properties: ScalaLibraryProperties =>
      languageLevel == properties.languageLevel &&
        compilerClasspath.map(_.getAbsolutePath) == properties.compilerClasspath.map(_.getAbsolutePath) &&
        scaladocExtraClasspath.map(_.getAbsolutePath) == properties.scaladocExtraClasspath.map(_.getAbsolutePath) &&
        compilerBridgeBinaryJar.map(_.getAbsolutePath) == properties.compilerBridgeBinaryJar.map(_.getAbsolutePath)
    case _ => false
  }

  override def hashCode: Int = languageLevel #+ compilerClasspath #+ scaladocExtraClasspath #+ compilerBridgeBinaryJar

  override def toString = s"ScalaLibraryProperties($languageLevel, $compilerClasspath, $scaladocExtraClasspath, $compilerBridgeBinaryJar)"
}

object ScalaLibraryProperties {

  import ScalaLanguageLevel._

  def apply(): ScalaLibraryProperties =
    apply(None, Seq.empty, Seq.empty, None)

  def apply(
    version: Option[String],
    compilerClasspath: Seq[File],
    scaladocExtraClasspath: Seq[File],
    compilerBridgeBinaryJar: Option[File]
  ): ScalaLibraryProperties = {
    val languageLevel = version.flatMap(findByVersion).getOrElse(getDefault)
    new ScalaLibraryProperties(
      languageLevel,
      compilerClasspath,
      scaladocExtraClasspath,
      compilerBridgeBinaryJar,
    )
  }

  private def urlToFile(url: String): File =
    new File(VfsUtilCore.urlToPath(url))

  private[project] def fileToUrl(file: File): String = {
    val canonicalPath = FileUtil.toCanonicalPath(file.getAbsolutePath)
    VfsUtilCore.pathToUrl(canonicalPath)
  }
}