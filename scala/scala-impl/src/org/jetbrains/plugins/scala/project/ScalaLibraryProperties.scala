package org.jetbrains.plugins.scala.project

import com.intellij.openapi.roots.libraries.LibraryProperties
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.util.HashBuilder._

import java.nio.file.Path

/**
 * @param _compilerClasspath       classpath required to instantiate a compiler
 * @param _scaladocExtraClasspath  extra classpath which is only required to generate scaladoc.
 *                                 It's not used during project compilation.
 *                                 In practice it's empty for Scala 2 and not empty for Scala 3.
 * @param _compilerBridgeBinaryJar optional compiler bridge jar.<br>
 *                                 When it's None, a bundled bridge will be used (see `Scala/lib/jps` directory in Scala plugin distribution).<br>
 *                                 Custom, non-bundled bridge is mostly required to be able to compile code
 *                                 with RC/Nightly versions of new Scala 3.x compiler
 */
final class ScalaLibraryProperties private(
  private[this] var _languageLevel: ScalaLanguageLevel,
  private[this] var _compilerClasspath: Seq[Path],
  private[this] var _scaladocExtraClasspath: Seq[Path],
  private[this] var _compilerBridgeBinaryJar: Option[Path],
  private var _replClasspath: ReplClasspath
) extends LibraryProperties[ScalaLibraryPropertiesState] {
  import ScalaLibraryProperties._

  def compilerBridgeBinaryJar: Option[Path] = _compilerBridgeBinaryJar
  def compilerBridgeBinaryJar_=(value: Option[Path]): Unit = _compilerBridgeBinaryJar = value

  def replClasspath: ReplClasspath = _replClasspath
  def replClasspath_=(value: ReplClasspath): Unit = _replClasspath = value

  def languageLevel: ScalaLanguageLevel = _languageLevel

  def languageLevel_=(languageLevel: ScalaLanguageLevel): Unit = {
    if (_languageLevel != languageLevel)
      settings.ScalaCompilerConfiguration.incModificationCount()
    _languageLevel = languageLevel
  }

  def compilerClasspath: Seq[Path] = _compilerClasspath
  def scaladocExtraClasspath: Seq[Path] = _scaladocExtraClasspath

  def compilerClasspath_=(classpath: Seq[Path]): Unit = {
    _compilerClasspath = classpath
  }
  def scaladocExtraClasspath_=(classpath: Seq[Path]): Unit = {
    _scaladocExtraClasspath = classpath
  }

  override def loadState(state: ScalaLibraryPropertiesState): Unit = {
    languageLevel = state.getLanguageLevel
    compilerClasspath = state.getCompilerClasspath.map(urlToFile).toSeq
    scaladocExtraClasspath = state.getScaladocExtraClasspath.map(urlToFile).toSeq
    compilerBridgeBinaryJar = Option(state.getCompilerBridgeBinaryJar).map(urlToFile)
    replClasspath = ReplClasspath.fromPaths(state.getReplClasspath.map(urlToFile).toSeq)
  }

  override def getState: ScalaLibraryPropertiesState = new ScalaLibraryPropertiesState(
    languageLevel,
    compilerClasspath.map(fileToUrl).toArray,
    scaladocExtraClasspath.map(fileToUrl).toArray,
    compilerBridgeBinaryJar.map(fileToUrl).orNull,
    replClasspath.asPaths.map(fileToUrl).toArray
  )

  override def equals(obj: Any): Boolean = obj match {
    case properties: ScalaLibraryProperties =>
      languageLevel == properties.languageLevel &&
        compilerClasspath.map(_.toCanonicalPath) == properties.compilerClasspath.map(_.toCanonicalPath) &&
        scaladocExtraClasspath.map(_.toCanonicalPath) == properties.scaladocExtraClasspath.map(_.toCanonicalPath) &&
        compilerBridgeBinaryJar.map(_.toCanonicalPath) == properties.compilerBridgeBinaryJar.map(_.toCanonicalPath) &&
        replClasspath.asPaths.map(_.toCanonicalPath) == properties.replClasspath.asPaths.map(_.toCanonicalPath)
    case _ => false
  }

  override def hashCode: Int = languageLevel #+ compilerClasspath #+ scaladocExtraClasspath #+ compilerBridgeBinaryJar #+ replClasspath

  override def toString = s"ScalaLibraryProperties($languageLevel, $compilerClasspath, $scaladocExtraClasspath, $compilerBridgeBinaryJar, $replClasspath)"
}

object ScalaLibraryProperties {

  import ScalaLanguageLevel._

  def apply(): ScalaLibraryProperties =
    apply(None, Seq.empty, Seq.empty, None, ReplClasspath.Bundled)

  def apply(
    version: Option[String],
    compilerClasspath: Seq[Path],
    scaladocExtraClasspath: Seq[Path],
    compilerBridgeBinaryJar: Option[Path],
    replClasspath: ReplClasspath
  ): ScalaLibraryProperties = {
    val languageLevel = version.flatMap(findByVersion).getOrElse(getDefault)
    new ScalaLibraryProperties(
      _languageLevel = languageLevel,
      _compilerClasspath = compilerClasspath,
      _scaladocExtraClasspath = scaladocExtraClasspath,
      _compilerBridgeBinaryJar = compilerBridgeBinaryJar,
      _replClasspath = replClasspath
    )
  }

  private def urlToFile(url: String): Path =
    Path.of(VfsUtilCore.urlToPath(url))

  private[project] def fileToUrl(file: Path): String = {
    val canonicalPath = FileUtil.toCanonicalPath(file.toCanonicalPath.toString)
    VfsUtilCore.pathToUrl(canonicalPath)
  }
}
