package org.jetbrains.plugins.scala
package project

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries._
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.{Icon, JComponent}

/**
  * @author Pavel Fatin
  */
final class ScalaLibraryType extends LibraryType[ScalaLibraryProperties](ScalaLibraryType.Kind) {

  override def getIcon: Icon = icons.Icons.SCALA_SDK

  override def getCreateActionName = "Scala SDK"

  override def createNewLibrary(parentComponent: JComponent,
                                contextDirectory: VirtualFile,
                                project: Project): NewLibraryConfiguration =
    template.ScalaLibraryDescription.createNewLibrary(parentComponent, contextDirectory)

  override def createPropertiesEditor(editorComponent: ui.LibraryEditorComponent[ScalaLibraryProperties]): ui.LibraryPropertiesEditor =
    new ScalaLibraryPropertiesEditor(editorComponent)
}

object ScalaLibraryType {

  def apply(): ScalaLibraryType =
    LibraryType.findByKind(Kind).asInstanceOf[ScalaLibraryType]

  private object Kind extends PersistentLibraryKind[ScalaLibraryProperties]("Scala") {

    override def createDefaultProperties() = ScalaLibraryProperties()
  }

}