package org.jetbrains.plugins.scala.config

import com.intellij.openapi.project.Project

/**
 * Pavel.Fatin, 04.08.2010
 */

object LibraryDescriptor {
  def compilersFor(project: Project): Array[LibraryDescriptor] = {
    def list(level: LibraryLevel) = Libraries.findBy(level, project).map { library =>
      LibraryDescriptor(LibraryId(library.getName, level), Some(new CompilerLibraryData(library)))
    }
    list(LibraryLevel.Global) ++ list(LibraryLevel.Project)
  }
  
  def createFor(id: LibraryId) = LibraryDescriptor(id, None)
}

case class LibraryDescriptor(val id: LibraryId, val data: Option[LibraryData])