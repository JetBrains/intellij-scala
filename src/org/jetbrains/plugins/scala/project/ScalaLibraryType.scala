package org.jetbrains.plugins.scala
package project

import com.intellij.openapi.roots.libraries.LibraryType
import com.intellij.openapi.roots.libraries.ui.{LibraryPropertiesEditor, LibraryEditorComponent}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.template.ScalaLibraryDescription
import org.jetbrains.plugins.scala.icons.Icons
import javax.swing.JComponent

/**
 * @author Pavel Fatin
 */
class ScalaLibraryType extends LibraryType[ScalaLibraryProperties](ScalaLibraryKind) {
  def getIcon = Icons.SCALA_SDK

  def getCreateActionName = "Scala SDK"

  def createNewLibrary(parentComponent: JComponent, contextDirectory: VirtualFile, project: Project) =
    ScalaLibraryDescription.createNewLibrary(parentComponent, contextDirectory)

  def createPropertiesEditor(editorComponent: LibraryEditorComponent[ScalaLibraryProperties]): LibraryPropertiesEditor =
    new ScalaLibraryPropertiesEditor(editorComponent)
}

object ScalaLibraryType {
  def instance: ScalaLibraryType =
    Option(LibraryType.findByKind(ScalaLibraryKind).asInstanceOf[ScalaLibraryType])
            .getOrElse(throw new NoSuchElementException("Scala library type not found"))
}