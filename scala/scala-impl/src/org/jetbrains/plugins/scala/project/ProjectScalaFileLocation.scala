package org.jetbrains.plugins.scala.project

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes

/** [[ScalaFileLocation]] from the project file index */
final class ProjectScalaFileLocation extends ScalaFileLocation {

  override def module(file: VirtualFile, project: Project): Option[Module] =
    Option(ProjectFileIndex.getInstance(project).getModuleForFile(file))

  override def isInLibrary(file: VirtualFile, project: Project): Boolean =
    ProjectFileIndex.getInstance(project).isInLibrary(file)

  override def isInLibrarySource(file: VirtualFile, project: Project): Boolean =
    ProjectFileIndex.getInstance(project).isInLibrarySource(file)

  override def isInLibraryClasses(file: VirtualFile, project: Project): Boolean =
    ProjectFileIndex.getInstance(project).isInLibraryClasses(file)

  override def isInSourceContent(file: VirtualFile, project: Project): Boolean =
    ProjectFileIndex.getInstance(project).isInSourceContent(file)

  override def isUnderSourceRoot(file: VirtualFile, project: Project): Boolean =
    ProjectFileIndex.getInstance(project).isUnderSourceRootOfType(file, JavaModuleSourceRootTypes.SOURCES)
}
