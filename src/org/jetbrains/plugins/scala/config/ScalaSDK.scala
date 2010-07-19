package org.jetbrains.plugins.scala.config

import java.io.File
/**
 * Pavel.Fatin, 06.07.2010
 */

abstract class ScalaSDK extends FileAPI {
  case class Pack(classes: String, sources: String, properties: String = "")
  
  // maven scala library
  // wrong path check
  // several libraries for module
  // 
  // valid - private? 
  // "get scala" link
  // listen to library changes
  // remove Options from public API
  // detailed validation before usage 
  // scalap
  // cache ScalaLibraries for module, lib changes -> expire, cache library validation result 

  protected val Compiler = Pack("scala-compiler.jar", "scala-compiler-src.jar", "compiler.properties")
  protected val Library = Pack("scala-library.jar", "scala-library-src.jar", "library.properties") 
  protected val Swing = Pack("scala-swing.jar", "scala-swing-src.jar")
  protected val Dbc = Pack("scala-dbc.jar", "scala-dbc-src.jar")

  private val VersionProperty = "version.number"
  
  private val SinceVersion = "2.8"
  
  def name: String

  protected def compilerFile: Option[File]
  
  protected def libraryFile: Option[File]
  
  def compilerPath = compilerFile.map(_.getPath).mkString
  
  def libraryPath = libraryFile.map(_.getPath).mkString
  
  def hasDocs: Boolean

  private def compilerVersion: Option[String] = { 
    compilerFile.flatMap(readProperty(_, Compiler.properties, VersionProperty))
  }
  
  private def libraryVersion: Option[String] = { 
    libraryFile.flatMap(readProperty(_, Library.properties, VersionProperty))
  }
  
  def consistent: Boolean = compilerVersion == libraryVersion 
  
  def version: Option[String] = compilerVersion
  
  def supported: Boolean = version.map(_.startsWith(SinceVersion)).getOrElse(false)
  
  def valid: Boolean = version.isDefined && consistent
}