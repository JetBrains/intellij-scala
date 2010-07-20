package org.jetbrains.plugins.scala.config

import java.io.File

/**
 * Pavel.Fatin, 06.07.2010
 */

abstract class ScalaSDK extends FileAPI {
  // vals
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
  
  case class Pack(classes: String, sources: String, properties: String = "")

  protected val Compiler = Pack("scala-compiler.jar", "scala-compiler-src.jar", "compiler.properties")
  protected val Library = Pack("scala-library.jar", "scala-library-src.jar", "library.properties") 
  protected val Swing = Pack("scala-swing.jar", "scala-swing-src.jar")
  protected val Dbc = Pack("scala-dbc.jar", "scala-dbc-src.jar")

  private val VersionProperty = "version.number"
  
  private val SinceVersion = "2.8"
  
  def name: String

  protected def compilerFile: Option[File]
  
  protected def libraryFile: Option[File]

  def hasDocs: Boolean
  
  def compilerPath: String = compilerFile.map(_.getPath).mkString
  
  def libraryPath: String = libraryFile.map(_.getPath).mkString

  def version: String = libraryVersion.getOrElse("Unknown")
  
  def valid: Boolean = libraryFile.isDefined
  
  private def compilerVersion: Option[String] =  
    compilerFile.flatMap(readProperty(_, Compiler.properties, VersionProperty))
  
  private def libraryVersion: Option[String] =  
    libraryFile.flatMap(readProperty(_, Library.properties, VersionProperty))
  
  def supported: Boolean = version.startsWith(SinceVersion)
  
  def check: Option[Problem] = {
    if(libraryFile.isEmpty) return Some(NotScalaSDK())
    
    if(compilerFile.isEmpty) return Some(ComplierMissing(version))
    
    if(libraryVersion.isEmpty) return Some(InvalidArchive(libraryFile.get))
    if(compilerVersion.isEmpty) return Some(InvalidArchive(compilerFile.get))
    
    if(compilerVersion != libraryVersion) return Some(InconsistentVersions(libraryVersion.get, compilerVersion.get))
    
    None
  }
}