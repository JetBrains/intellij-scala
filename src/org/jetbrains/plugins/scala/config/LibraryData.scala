package org.jetbrains.plugins.scala
package config

import java.io.File
import FileAPI._
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library

/**
 * Pavel.Fatin, 26.07.2010
 */


abstract class LibraryData(delegate: Library, prefix: String, bundle: String) {
  def valid: Boolean = jar.isDefined

  def version: String = jar.flatMap(readProperty(_, bundle, "version.number")).mkString 

  def classpath: String = files.map(_.getPath).mkString(File.pathSeparator)
  
  def files: Seq[File] = delegate.getFiles(OrderRootType.CLASSES).map(_.toFile)
  
  def jar: Option[File] = files.find(_.getName.startsWith(prefix)) 
}

class CompilerLibraryData(delegate: Library) 
        extends LibraryData(delegate, "scala-compiler", "compiler.properties")

class ScalaLibraryData(delegate: Library) 
        extends LibraryData(delegate, "scala-library", "library.properties")