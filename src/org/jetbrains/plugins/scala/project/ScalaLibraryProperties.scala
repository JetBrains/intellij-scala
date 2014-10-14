package org.jetbrains.plugins.scala
package project

import java.io.File
import com.intellij.openapi.roots.libraries.LibraryProperties
import com.intellij.openapi.util.io.FileUtil._
import com.intellij.openapi.vfs.VfsUtilCore._

/**
 * @author Pavel Fatin
 */
class ScalaLibraryProperties extends LibraryProperties[ScalaLibraryPropertiesState] {
  var languageLevel: ScalaLanguageLevel = _
  var compilerClasspath: Seq[File] = _

  loadState(new ScalaLibraryPropertiesState())

  def loadState(state: ScalaLibraryPropertiesState) {
    languageLevel = state.languageLevel
    compilerClasspath = state.compilerClasspath.map(path => new File(urlToPath(path)))
  }

  def getState = {
    val state = new ScalaLibraryPropertiesState()
    state.languageLevel = languageLevel
    state.compilerClasspath = compilerClasspath.map(file => pathToUrl(toCanonicalPath(file.getAbsolutePath))).toArray
    state
  }

  override def equals(obj: scala.Any) = obj match {
    case that: ScalaLibraryProperties =>
      languageLevel == that.languageLevel && compilerClasspath == that.compilerClasspath
    case _ => false
  }

  override def hashCode() = languageLevel.hashCode * 31 + compilerClasspath.hashCode
}
