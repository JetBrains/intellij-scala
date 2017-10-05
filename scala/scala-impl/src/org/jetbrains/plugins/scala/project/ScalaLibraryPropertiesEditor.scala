package org.jetbrains.plugins.scala
package project

import javax.swing.JComponent

import com.intellij.openapi.roots.libraries.ui.{LibraryEditorComponent, LibraryPropertiesEditor}

/**
 * @author Pavel Fatin
 */
private class ScalaLibraryPropertiesEditor(editorComponent: LibraryEditorComponent[ScalaLibraryProperties]) extends LibraryPropertiesEditor {

  private val form = new ScalaLibraryEditorForm()

  override def createComponent(): JComponent = form.getComponent

  override def isModified: Boolean = form.getState != properties.getState

  override def reset(): Unit = {
    form.setState(properties.getState)
  }

  override def apply(): Unit = {
    properties.loadState(form.getState)
  }

  private def properties = editorComponent.getProperties
}
