package org.jetbrains.plugins.scala
package config

import com.intellij.openapi.project.Project
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.impl.libraries.{ProjectLibraryTable, ApplicationLibraryTable}
import com.intellij.openapi.roots.libraries.Library.ModifiableModel
import com.intellij.openapi.roots._
import com.intellij.openapi.roots.libraries.{Library, LibraryTable}
import org.jetbrains.plugins.scala.extensions._

/**
 * Pavel.Fatin, 04.08.2010
 */

object Libraries {
  def findBy(id: LibraryId, project: Project): Option[Library] = 
    findBy(id.level, project).find(_.getName == id.name)
  
  def findBy(level: LibraryLevel, project: Project): Array[Library] = level match {
    case LibraryLevel.Global => globalLibraries.toArray
    case LibraryLevel.Project => projectLibraries(project).toArray
    case _ => Array.empty
  }

  private def globalLibraries: Seq[Library] = ApplicationLibraryTable.getApplicationTable.getLibraries

  private def projectLibraries(project: Project): Array[Library] = 
    if(project == null) Array.empty else ProjectLibraryTable.getInstance(project).getLibraries
  
  private def moduleLibraries(module: Module) = inReadAction {
    ModuleRootManager.getInstance(module).getOrderEntries.view
            .filter(_.isInstanceOf[LibraryOrderEntry])
            .map(_.asInstanceOf[LibraryOrderEntry].getLibrary)
            .filter(_ != null)
  }
  
  def nameClashes(name: String, level: LibraryLevel, project: Project): Boolean = 
    findBy(level, project).exists(_.getName == name)
  
  def uniqueName(name: String, level: LibraryLevel, project: Project): String = {
    def postfix(name: String, n: Int = 1): String = {
      val postfixed = "%s (%d)".format(name, n)  
      if(nameClashes(postfixed, level, project)) postfix(name, n + 1) else postfixed
    }
    val pure  = name.replaceFirst("""\(\d+\)$""", "")
    if(nameClashes(pure, level, project)) postfix(pure) else pure 
  }
  
   def create(id: LibraryId, rootModel: ModifiableRootModel, attach: Boolean)
                           (action: ModifiableModel => Unit) = {
      val libraryTable: LibraryTable = id.level match {
        case LibraryLevel.Global => ApplicationLibraryTable.getApplicationTable
        case LibraryLevel.Project => ProjectLibraryTable.getInstance(rootModel.getProject)
        case LibraryLevel.Module =>  rootModel.getModuleLibraryTable
      }

    val libraryTableModel = libraryTable.getModifiableModel
    val library = libraryTableModel.createLibrary(id.name)
    val libraryModel = library.getModifiableModel

    action(libraryModel)

    if(attach && id.level != LibraryLevel.Module) {
      rootModel.addLibraryEntry(library)
    }

    libraryModel.commit()
    libraryTableModel.commit()

    library
  }
}