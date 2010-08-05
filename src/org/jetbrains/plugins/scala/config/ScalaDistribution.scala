package org.jetbrains.plugins.scala
package config

import java.io.File
import FileAPI._
import com.intellij.openapi.roots.impl.libraries.{ProjectLibraryTable, ApplicationLibraryTable}
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.{ModifiableRootModel, JavadocOrderRootType, OrderRootType}
import com.intellij.openapi.roots.libraries.Library.ModifiableModel

/**
 * Pavel.Fatin, 01.07.2010
 */

object ScalaDistribution {
  def findHome: Option[String] = {
    Option(System.getenv("SCALA_HOME")).orElse {
      Option(System.getenv("PATH")).flatMap {
        _.split(File.pathSeparator)
                .find(_.toLowerCase.contains("scala"))
                .map(_.replaceFirst(File.separator + "?bin$", ""))
      }
    }
  }
}

class ScalaDistribution(val home: File) {
  private case class Pack(classes: String, sources: String, properties: String = "")

  private val Compiler = Pack("scala-compiler.jar", "scala-compiler-src.jar", "compiler.properties")
  private val Library = Pack("scala-library.jar", "scala-library-src.jar", "library.properties") 
  private val Swing = Pack("scala-swing.jar", "scala-swing-src.jar")
  private val Dbc = Pack("scala-dbc.jar", "scala-dbc-src.jar")

  private val VersionProperty = "version.number"
  private val SupportedVersion = """^(?:2\.7|2\.8)""".r
  
  private val Libs = List(Library, Swing, Dbc)
  private val Lib = home / "lib"
  private val Src = home / "src"
  private def classes = Lib / Libs.map(_.classes)
  private def sources = Src / Libs.map(_.sources)
  private def docs = home / "doc" / "scala-devel-docs" / "api"
  
  private val compilerFile = optional(Lib / Compiler.classes)
  private val libraryFile = optional(Lib / Library.classes)
  
  private def compilerVersionOption: Option[String] =  
    compilerFile.flatMap(readProperty(_, Compiler.properties, VersionProperty))
  
  private def libraryVersionOption: Option[String] =  
    libraryFile.flatMap(readProperty(_, Library.properties, VersionProperty))
  
  def hasDocs = docs.exists

  def missing: String = (classes ++ sources).filterNot(_ exists).map(_.getName).mkString(", ")
  
  def compilerPath: String = compilerFile.map(_.getPath).mkString
  
  def libraryPath: String = libraryFile.map(_.getPath).mkString

  def compilerVersion: String = compilerVersionOption.map(_.replaceFirst("\\.final", "")).mkString
  
  def libraryVersion: String = libraryVersionOption.map(_.replaceFirst("\\.final", "")).mkString
  
  def valid: Boolean = libraryFile.isDefined
  
  def version = compilerVersion
  
  def createCompilerLibrary(id: LibraryId, rootModel: ModifiableRootModel) = 
    Libraries.create(id, rootModel, false) { model =>
      compilerFile.foreach(it => model.addRoot(it.toLibraryRootURL, OrderRootType.CLASSES))
      libraryFile.foreach(it => model.addRoot(it.toLibraryRootURL, OrderRootType.CLASSES))
    }
  
  def createStandardLibrary(id: LibraryId, rootModel: ModifiableRootModel) = 
    Libraries.create(id, rootModel, true) { model =>
      classes.foreach(it => model.addRoot(it.toLibraryRootURL, OrderRootType.CLASSES))
      sources.foreach(it => model.addRoot(it.toLibraryRootURL, OrderRootType.SOURCES))
      model.addRoot(docs.toLibraryRootURL, JavadocOrderRootType.getInstance)
    }
}