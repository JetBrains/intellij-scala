package org.jetbrains.plugins.scala
package project

import java.io.File
import java.util.Objects

import com.intellij.openapi.roots.libraries.LibraryProperties
import com.intellij.openapi.util.io.FileUtil._
import com.intellij.openapi.vfs.VfsUtilCore._
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration

/**
 * @author Pavel Fatin
 */
class ScalaLibraryProperties extends LibraryProperties[ScalaLibraryPropertiesState] {
  var platform: Platform = _
  var compilerClasspath: Seq[File] = _

  private var _languageLevel: ScalaLanguageLevel = _

  def languageLevel: ScalaLanguageLevel = _languageLevel

  def languageLevel_=(newLanguageLevel: ScalaLanguageLevel): Unit = {
    if (_languageLevel != newLanguageLevel) {
      ScalaCompilerConfiguration.incModificationCount()
    }
    _languageLevel = newLanguageLevel
  }

  loadState(new ScalaLibraryPropertiesState())

  def loadState(state: ScalaLibraryPropertiesState) {
    platform = state.getPlatform
    languageLevel = state.getLanguageLevel
    compilerClasspath = state.compilerClasspath
      .map(urlToPath)
      .map(new File(_))
  }

  def getState: ScalaLibraryPropertiesState = {
    val compilerClasspath = this.compilerClasspath
      .map(_.getAbsolutePath)
      .map(toCanonicalPath)
      .map(pathToUrl)
      .toArray
    new ScalaLibraryPropertiesState(platform, languageLevel, compilerClasspath)
  }

  override def equals(obj: scala.Any): Boolean = obj match {
    case that: ScalaLibraryProperties =>
      platform == that.platform &&
      languageLevel == that.languageLevel &&
        compilerClasspath == that.compilerClasspath
    case _ => false
  }

  override def hashCode(): Int = Objects.hash(platform, languageLevel, compilerClasspath)
}
