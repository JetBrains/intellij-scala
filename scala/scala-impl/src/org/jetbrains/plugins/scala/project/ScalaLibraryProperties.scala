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
final class ScalaLibraryProperties private(private[this] var _platform: Platform,
                                           private[this] var _languageLevel: ScalaLanguageLevel,
                                           private[this] var _compilerClasspath: Seq[File]) extends LibraryProperties[ScalaLibraryPropertiesState] {

  def platform: Platform = _platform

  def platform_=(platform: Platform): Unit = {
    _platform = platform
  }

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
    platform = state.getPlatform
    languageLevel = state.getLanguageLevel
    compilerClasspath = state.compilerClasspath
      .map(VfsUtilCore.urlToPath)
      .map(new File(_))
  }

  def getState: ScalaLibraryPropertiesState = {
    val compilerClasspath = this.compilerClasspath
      .map(_.getAbsolutePath)
      .map(FileUtil.toCanonicalPath)
      .map(VfsUtilCore.pathToUrl)
      .toArray

    new ScalaLibraryPropertiesState(
      platform,
      languageLevel,
      compilerClasspath
    )
  }

  override def equals(obj: Any): Boolean = obj match {
    case ScalaLibraryProperties(platform, languageLevel, compilerClasspath) =>
      _platform == platform &&
        _languageLevel == languageLevel &&
        _compilerClasspath == compilerClasspath
    case _ => false
  }

  override def hashCode: Int = Objects.hash(
    platform,
    languageLevel,
    compilerClasspath
  )

  override def toString = s"ScalaLibraryProperties($platform, $languageLevel, $compilerClasspath)"
}

object ScalaLibraryProperties {

  def apply(state: ScalaLibraryPropertiesState = new ScalaLibraryPropertiesState()): ScalaLibraryProperties = {
    val properties = new ScalaLibraryProperties(
      null,
      null,
      Seq.empty
    )
    properties.loadState(state)
    properties
  }

  def apply(platform: Platform,
            languageLevel: ScalaLanguageLevel,
            compilerClasspath: Seq[File]) = new ScalaLibraryProperties(
    platform,
    languageLevel,
    compilerClasspath
  )

  def unapply(libraryProperties: LibraryProperties[_]): Option[(Platform, ScalaLanguageLevel, Seq[File])] =
    libraryProperties match {
      case scalaLibraryProperties: ScalaLibraryProperties =>
        Some(
          scalaLibraryProperties.platform,
          scalaLibraryProperties.languageLevel,
          scalaLibraryProperties.compilerClasspath
        )
      case _ => None
    }
}