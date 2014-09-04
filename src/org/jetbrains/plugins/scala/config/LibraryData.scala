package org.jetbrains.plugins.scala
package config

import java.io.File

import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.plugins.scala.config.FileAPI._
/**
 * Pavel.Fatin, 26.07.2010
 */


abstract class LibraryData(protected val delegate: Library, name: String, 
                           prefix: String, bundle: String, marker: String) {
  def version: Option[String] = jar.flatMap(readProperty(_, bundle, "version.number"))

  def classpath: String = files.map(_.getPath).mkString(File.pathSeparator)
  
  def files: Seq[File] = delegate.getFiles(OrderRootType.CLASSES).map(_.toFile)
  
  def jar: Option[File] = jars.headOption
  
  private def jars: Seq[File] = files.filter(_.getName.startsWith(prefix))
  
  def problem: Option[String] = {
    if(jar.isEmpty)
      return Some("no %s*.jar found".format(prefix))

    if(jars.size > 1) 
      return Some("multiple %s*.jar's attached".format(prefix))
        
    if(version.isEmpty) 
      return Some("unable to read %s version".format(jar.get.getName))
    
    if(!jar.exists(exists(_, marker)))
      return Some("no %s classes found in %s".format(name, jar.get.getName))
    
    None
  }
}

class CompilerLibraryData(delegate: Library) extends LibraryData(delegate, "Scala compiler", 
  "scala-compiler", "compiler.properties", "scala/tools/nsc/Main.class") {
  
  override def problem: Option[String] = {
    if(files.exists(_.isDirectory)) return None
    
    val standardLibraryData = new StandardLibraryData(delegate)
    super.problem.orElse(standardLibraryData.problem).orElse {
      val standardLibraryVersion = standardLibraryData.version
      if (version != standardLibraryVersion) 
        Some("versions mismatch, compiler: %s, standard library: %s".format(
          version.mkString, standardLibraryVersion.mkString)) 
      else 
        None
    }
  }
}

class StandardLibraryData(delegate: Library) extends LibraryData(delegate, "Scala standard library", 
  "scala-library", "library.properties", "scala/Array.class") 