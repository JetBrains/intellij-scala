package org.jetbrains.plugins.scala.config.ui

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.config._

/**
 * Pavel.Fatin, 04.08.2010
 */

object LibraryDescriptor {
  def compilersFor(project: Project): Array[LibraryDescriptor] = {
    def list(level: LibraryLevel) = Libraries.findBy(level, project).map { library =>
      LibraryDescriptor(LibraryId(library.getName, level), Some(new CompilerLibraryData(library)))
    }
    val all = list(LibraryLevel.Global) ++ list(LibraryLevel.Project)
    val (suitable, remaining) = all.partition(_.data.get.problem.isEmpty)
    suitable.sortBy(_.data.get.version.get).reverse ++ remaining.sortBy(_.id.name.toLowerCase)
  }
  
  def createFor(id: LibraryId) = LibraryDescriptor(id, None)
}

case class LibraryDescriptor(id: LibraryId, data: Option[LibraryData])