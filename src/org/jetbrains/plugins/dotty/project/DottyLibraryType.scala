package org.jetbrains.plugins.dotty.project

import javax.swing.{Icon, JComponent}

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.ui.{LibraryEditorComponent, LibraryPropertiesEditor}
import com.intellij.openapi.roots.libraries.{LibraryProperties, LibraryType}
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.dotty.project.template.DottyLibraryDescription
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.project.ScalaLibraryProperties

/**
  * @author Nikolay.Tropin
  */
class DottyLibraryType extends LibraryType[ScalaLibraryProperties](DottyLibraryKind) {

  override def getIcon = Icons.SCALA_SDK

  override def getIcon(properties: LibraryProperties[_]): Icon = Icons.SCALA_SDK

  def getCreateActionName = "Dotty SDK"

  def createNewLibrary(parentComponent: JComponent, contextDirectory: VirtualFile, project: Project) =
    DottyLibraryDescription.createNewLibrary(parentComponent, contextDirectory)

  def createPropertiesEditor(editorComponent: LibraryEditorComponent[ScalaLibraryProperties]): LibraryPropertiesEditor =
    new DottyLibraryPropertiesEditor(editorComponent)
}

object DottyLibraryType {

  def instance = Option(LibraryType.findByKind(DottyLibraryKind).asInstanceOf[DottyLibraryType])
    .getOrElse(throw new NoSuchElementException("Dotty library type not found"))
}
