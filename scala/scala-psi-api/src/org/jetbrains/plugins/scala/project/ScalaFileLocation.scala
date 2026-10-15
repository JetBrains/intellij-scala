package org.jetbrains.plugins.scala.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/**
 * Where a file is in the project model: its module, and whether it belongs to a library.
 *
 * This is backend data (the project file index), so the language layer only knows this interface. Without an
 * implementation (e.g. on a frontend) files have no module and are not in libraries.
 *
 * @see [[ScalaFileFeatures]]
 */
@ApiStatus.Internal
trait ScalaFileLocation {

  def module(file: VirtualFile, project: Project): Option[Module]

  /** Whether the file is in the classes or the sources of a library */
  def isInLibrary(file: VirtualFile, project: Project): Boolean

  def isInLibrarySource(file: VirtualFile, project: Project): Boolean

  def isInLibraryClasses(file: VirtualFile, project: Project): Boolean

  def isInSourceContent(file: VirtualFile, project: Project): Boolean

  /** Whether the file is under a source or test source root; unlike [[isInSourceContent]], not under a resource root */
  def isUnderSourceRoot(file: VirtualFile, project: Project): Boolean
}

object ScalaFileLocation {

  def instance: ScalaFileLocation =
    ApplicationManager.getApplication.getService(classOf[ScalaFileLocation]) match {
      case null    => NoFileLocation
      case service => service
    }

  private object NoFileLocation extends ScalaFileLocation {
    override def module(file: VirtualFile, project: Project): Option[Module] = None

    override def isInLibrary(file: VirtualFile, project: Project): Boolean = false

    override def isInLibrarySource(file: VirtualFile, project: Project): Boolean = false

    override def isInLibraryClasses(file: VirtualFile, project: Project): Boolean = false

    override def isInSourceContent(file: VirtualFile, project: Project): Boolean = false

    override def isUnderSourceRoot(file: VirtualFile, project: Project): Boolean = false
  }
}
