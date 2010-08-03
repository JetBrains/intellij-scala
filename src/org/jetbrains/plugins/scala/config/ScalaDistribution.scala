package org.jetbrains.plugins.scala
package config

import java.io.File
import FileAPI._
import com.intellij.openapi.roots.impl.libraries.{ProjectLibraryTable, ApplicationLibraryTable}
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer.LibraryLevel
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
  
  private def compilerVersion: Option[String] =  
    compilerFile.flatMap(readProperty(_, Compiler.properties, VersionProperty))
  
  private def libraryVersion: Option[String] =  
    libraryFile.flatMap(readProperty(_, Library.properties, VersionProperty))
  
  def name = "Scala-%s".format(version)
  
  def hasDocs = docs.exists

  def missing: String = (classes ++ sources).filterNot(_ exists).map(_.getName).mkString(", ")
  
  def compilerPath: String = compilerFile.map(_.getPath).mkString
  
  def libraryPath: String = libraryFile.map(_.getPath).mkString

  def version: String = libraryVersion.map(_.replaceFirst("\\.final", "")).getOrElse("Unknown")
  
  def valid: Boolean = libraryFile.isDefined
  
  def problems: Array[Problem] = {
    "" match {
      case _ if libraryFile.isEmpty => Array(NotScalaSDK())  
      case _ if compilerFile.isEmpty => Array(ComplierMissing(version))  
      case _ if libraryVersion.isEmpty => Array(InvalidArchive(libraryFile.get))  
      case _ if compilerVersion.isEmpty => Array(InvalidArchive(compilerFile.get))  
      case _ if compilerVersion != libraryVersion => Array(InconsistentVersions(libraryVersion.get, compilerVersion.get))
      case _ if SupportedVersion.findFirstIn(version).isEmpty => Array(UnsupportedVersion(version))
      case _ => Array.empty
    }
  }

  def createCompilerLibrary(name: String, level: LibraryLevel, rootModel: ModifiableRootModel) = 
    createLibrary(name, level, rootModel, false) { model =>
      compilerFile.foreach(it => model.addRoot(it.toLibraryRootURL, OrderRootType.CLASSES))
      libraryFile.foreach(it => model.addRoot(it.toLibraryRootURL, OrderRootType.CLASSES))
    }
  
  def createStandardLibrary(name: String, level: LibraryLevel, rootModel: ModifiableRootModel) = 
    createLibrary(name, level, rootModel, true) { model =>
      classes.foreach(it => model.addRoot(it.toLibraryRootURL, OrderRootType.CLASSES))
      sources.foreach(it => model.addRoot(it.toLibraryRootURL, OrderRootType.SOURCES))
      model.addRoot(docs.toLibraryRootURL, JavadocOrderRootType.getInstance)
    }
  
  private def createLibrary(name: String, level: LibraryLevel, rootModel: ModifiableRootModel, attach: Boolean)
                           (action: ModifiableModel => Unit) = {
      val libraryTable: LibraryTable = level match {
        case LibraryLevel.GLOBAL => ApplicationLibraryTable.getApplicationTable
        case LibraryLevel.PROJECT => ProjectLibraryTable.getInstance(rootModel.getProject)
        case LibraryLevel.MODULE =>  rootModel.getModuleLibraryTable
      }

    val libraryTableModel = libraryTable.getModifiableModel
    val library = libraryTableModel.createLibrary(name)
    val libraryModel = library.getModifiableModel

    action(libraryModel)

    if(attach && level != LibraryLevel.MODULE) {
      rootModel.addLibraryEntry(library)
    }

    libraryModel.commit()
    libraryTableModel.commit()

    library
  }
}