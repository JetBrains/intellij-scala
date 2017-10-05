package org.jetbrains.plugins.scala
package project

import com.intellij.openapi.roots.libraries.{LibraryProperties, LibraryType, NewLibraryConfiguration}
import com.intellij.openapi.roots.libraries.ui.{LibraryEditorComponent, LibraryPropertiesEditor}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.template.ScalaLibraryDescription
import org.jetbrains.plugins.scala.icons.Icons
import javax.swing.{Icon, JComponent}

/**
 * @author Pavel Fatin
 */
class ScalaLibraryType extends LibraryType[ScalaLibraryProperties](ScalaLibraryKind) {
  override def getIcon = Icons.SCALA_SDK

  override def getIcon(properties: ScalaLibraryProperties): Icon = Icons.SCALA_SDK

  def getCreateActionName = "Scala SDK"

  def createNewLibrary(parentComponent: JComponent, contextDirectory: VirtualFile, project: Project): NewLibraryConfiguration =
    ScalaLibraryDescription.createNewLibrary(parentComponent, contextDirectory)

  def createPropertiesEditor(editorComponent: LibraryEditorComponent[ScalaLibraryProperties]): LibraryPropertiesEditor =
    new ScalaLibraryPropertiesEditor(editorComponent)
}

object ScalaLibraryType {
  def instance: ScalaLibraryType =
    Option(LibraryType.findByKind(ScalaLibraryKind).asInstanceOf[ScalaLibraryType])
            .getOrElse(throw new NoSuchElementException("Scala library type not found"))
}