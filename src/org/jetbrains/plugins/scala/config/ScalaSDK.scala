package org.jetbrains.plugins.scala.config

import java.io.File

/**
 * Pavel.Fatin, 06.07.2010
 */

abstract class ScalaSDK extends FileAPI {
  // wrong path check
  // valid - private? 
  // "get scala" link
  // listen to library changes
  // cache ScalaLibraries for module, lib changes -> expire, cache library validation result 
  
  case class Pack(classes: String, sources: String, properties: String = "")

  protected val Compiler = Pack("scala-compiler.jar", "scala-compiler-src.jar", "compiler.properties")
  protected val Library = Pack("scala-library.jar", "scala-library-src.jar", "library.properties") 
  protected val Swing = Pack("scala-swing.jar", "scala-swing-src.jar")
  protected val Dbc = Pack("scala-dbc.jar", "scala-dbc-src.jar")

  private val VersionProperty = "version.number"
  
  private val supportedVersion = """^(?:2\.7|2\.8)""".r
  
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
  
  def problems: Array[Problem] = {
    "" match {
      case _ if libraryFile.isEmpty => Array(NotScalaSDK())  
      case _ if compilerFile.isEmpty => Array(ComplierMissing(version))  
      case _ if libraryVersion.isEmpty => Array(InvalidArchive(libraryFile.get))  
      case _ if compilerVersion.isEmpty => Array(InvalidArchive(compilerFile.get))  
      case _ if compilerVersion != libraryVersion => Array(InconsistentVersions(libraryVersion.get, compilerVersion.get))
      case _ if supportedVersion.findFirstIn(version).isEmpty => Array(UnsupportedVersion(version))
      case _ => Array.empty
    }
  }
}