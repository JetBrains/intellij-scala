package org.jetbrains.plugins.scala
package configuration

import com.intellij.openapi.roots.libraries.LibraryProperties
import collection.JavaConverters._
import java.io.File
import com.intellij.openapi.util.io.FileUtil._
import com.intellij.openapi.vfs.VfsUtilCore._

/**
 * @author Pavel Fatin
 */
class ScalaLibraryProperties extends LibraryProperties[ScalaLibraryState] {
  var compilerClasspath: Seq[File] = Seq.empty

  def loadState(state: ScalaLibraryState) {
    val paths = state.compilerClasspath.asScala
    compilerClasspath = paths.map(path => new File(urlToPath(path)))
  }

  def getState = {
    val paths = compilerClasspath.map(file => pathToUrl(toCanonicalPath(file.getAbsolutePath)))
    val state = new ScalaLibraryState()
    state.compilerClasspath = paths.asJava
    state
  }

  override def equals(obj: scala.Any) = obj match {
    case other: ScalaLibraryProperties => compilerClasspath == other.compilerClasspath
    case _ => false
  }

  override def hashCode() = compilerClasspath.hashCode
}
