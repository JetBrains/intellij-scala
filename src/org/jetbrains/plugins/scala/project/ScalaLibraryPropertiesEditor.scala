package org.jetbrains.plugins.scala
package project

import javax.swing.JComponent

import com.intellij.openapi.roots.libraries.ui.{LibraryEditorComponent, LibraryPropertiesEditor}

/**
 * @author Pavel Fatin
 */
private class ScalaLibraryPropertiesEditor(editorComponent: LibraryEditorComponent[ScalaLibraryProperties]) extends LibraryPropertiesEditor {
  private val form = new ScalaLibraryEditorForm()

  def createComponent(): JComponent = form.getComponent

  def isModified: Boolean = form.getState != properties.getState

  def reset() {
    form.setState(properties.getState)
  }

  def apply() {
    properties.loadState(form.getState)
  }

  private def properties = editorComponent.getProperties
}
