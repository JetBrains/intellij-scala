package org.jetbrains.plugins.scala
package project

import java.io.File
import java.util.Objects

import com.intellij.openapi.roots.libraries.LibraryProperties
import com.intellij.openapi.util.io.FileUtil._
import com.intellij.openapi.vfs.VfsUtilCore._

/**
 * @author Pavel Fatin
 */
class ScalaLibraryProperties extends LibraryProperties[ScalaLibraryPropertiesState] {
  var platform: Platform = _
  var languageLevel: ScalaLanguageLevel = _
  var compilerClasspath: Seq[File] = _

  loadState(new ScalaLibraryPropertiesState())

  def loadState(state: ScalaLibraryPropertiesState) {
    platform = Platform.from(state.platform)
    languageLevel = ScalaLanguageLevel.from(state.languageLevel)
    compilerClasspath = state.compilerClasspath.map(path => new File(urlToPath(path)))
  }

  def getState: ScalaLibraryPropertiesState = {
    val state = new ScalaLibraryPropertiesState()
    state.platform = platform.proxy
    state.languageLevel = languageLevel.proxy
    state.compilerClasspath = compilerClasspath.map(file => pathToUrl(toCanonicalPath(file.getAbsolutePath))).toArray
    state
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
