package org.jetbrains.plugins.scala
package configuration

import com.intellij.openapi.roots.libraries.ui.{LibraryPropertiesEditor, LibraryEditorComponent}
import com.intellij.openapi.projectRoots.ui.PathEditor
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import javax.swing.{Box, JLabel, JPanel}
import java.awt.{Font, BorderLayout}
import com.intellij.ui.IdeBorderFactory
import com.intellij.openapi.vfs.{VfsUtilCore, VfsUtil}
import collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
private class ScalaLibraryPropertiesEditor(editorComponent: LibraryEditorComponent[ScalaLibraryProperties]) extends LibraryPropertiesEditor {
  private val editor = {
    val descriptor = new FileChooserDescriptor(true, false, true, false, false, true)
    new PathEditor(descriptor)
  }

  def createComponent() = {
    val compilerPanel = {
      val component = editor.createComponent()
      component.setBorder(IdeBorderFactory.createBorder())

      val panel = new JPanel(new BorderLayout())
      panel.add(Box.createVerticalStrut(10), BorderLayout.NORTH)
      panel.add(ScalaLibraryPropertiesEditor.titleLabel("Scala compiler classpath:"), BorderLayout.CENTER)
      panel.add(component, BorderLayout.SOUTH)
      panel
    }

    val panel = new JPanel(new BorderLayout(0, 15))
    panel.add(compilerPanel, BorderLayout.CENTER)
    panel.add(ScalaLibraryPropertiesEditor.titleLabel("Scala library:"), BorderLayout.SOUTH)
    panel
  }

  def isModified = editor.isModified

  def reset() {
    val files = properties.compilerClasspath.map(VfsUtil.findFileByIoFile(_, true))
    editor.resetPath(files.asJava)
  }

  def apply() {
    val roots = editor.getRoots.toSeq
    properties.compilerClasspath = roots.map(VfsUtilCore.virtualToIoFile)
  }

  private def properties = editorComponent.getProperties
}

private object ScalaLibraryPropertiesEditor {
  private def titleLabel(title: String): JLabel = {
    val label = new JLabel(title)
    label.setFont(label.getFont.deriveFont(Font.BOLD))
    label
  }
}