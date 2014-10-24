package org.jetbrains.plugins.scala
package project

import com.intellij.openapi.roots.libraries.ui.{LibraryPropertiesEditor, LibraryEditorComponent}

/**
 * @author Pavel Fatin
 */
private class ScalaLibraryPropertiesEditor(editorComponent: LibraryEditorComponent[ScalaLibraryProperties]) extends LibraryPropertiesEditor {
  private lazy val form = new ScalaLibraryEditorForm()

  def createComponent() = form.getComponent

  def isModified = form.getState != properties.getState

  def reset() {
    form.setState(properties.getState)
  }

  def apply() {
    properties.loadState(form.getState)
  }

  private def properties = editorComponent.getProperties
}
