package org.jetbrains.plugins.scala
package config

import java.io.File
import com.intellij.openapi.project.Project
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.impl.libraries.{ProjectLibraryTable, ApplicationLibraryTable}
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer.LibraryLevel
import com.intellij.openapi.roots.libraries.{Library, LibraryTable}
import com.intellij.openapi.roots.{ModifiableRootModel, JavadocOrderRootType, ModuleRootManager, OrderRootType}

/**
 * Pavel.Fatin, 01.07.2010
 */

object ScalaDistribution {
  def findHome: Option[String] = {
    Option(System.getenv("SCALA_HOME")).orElse {
      Option(System.getenv("PATH")).flatMap {
        _.split(System.getProperty("path.separator"))
                .find(_.toLowerCase.contains("scala"))
                .map(_.replaceFirst(System.getProperty("file.separator") + "?bin$", ""))
      }
    }
  }
}

class ScalaDistribution(val home: File) extends ScalaSDK {
  private val Libs = List(Library, Swing, Dbc)
  
  private val Lib = home / "lib"
  
  private val Src = home / "src"

  private def classes = Lib / Libs.map(_.classes)
  
  private def sources = Src / Libs.map(_.sources)
  
  private def docs = home / "docs" / "api"
  
  override protected val compilerFile = optional(Lib / Compiler.classes)
  
  override protected val libraryFile = optional(Lib / Library.classes)
  
  override def name = version.map("Scala-" + _).getOrElse("Unknown")
  
  override def hasDocs = docs.exists

  def missing = (classes ++ sources).filterNot(_ exists).map(_.getName).mkString(", ")
  
  def createLibrary(name: String, level: LibraryLevel, project: Project, rootModel: ModifiableRootModel): Library = {
      val libraryTable: LibraryTable = level match {
        case LibraryLevel.GLOBAL => ApplicationLibraryTable.getApplicationTable
        case LibraryLevel.PROJECT => ProjectLibraryTable.getInstance(project)
        case LibraryLevel.MODULE =>  rootModel.getModuleLibraryTable
      }

    val libraryTableModel = libraryTable.getModifiableModel
    val library = libraryTableModel.createLibrary(name)
    val libraryModel = library.getModifiableModel

    classes.foreach(it => libraryModel.addRoot(it.toLibraryRootURL, OrderRootType.CLASSES))
    sources.foreach(it => libraryModel.addRoot(it.toLibraryRootURL, OrderRootType.SOURCES))

    libraryModel.addRoot(docs.toLibraryRootURL, JavadocOrderRootType.getInstance)

    if(level != LibraryLevel.MODULE) {
      rootModel.addLibraryEntry(library)
    }

    libraryModel.commit()
    libraryTableModel.commit()

    library
  }
}