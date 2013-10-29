package org.jetbrains.plugins.scala
package configuration

import com.intellij.openapi.roots.libraries.ui.{LibraryPropertiesEditor, LibraryEditorComponent}
import com.intellij.openapi.projectRoots.ui.PathEditor
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.ui.TabbedPaneWrapper
import com.intellij.openapi.vfs.{VfsUtilCore, VfsUtil}
import collection.JavaConverters._
import com.intellij.openapi.util.Disposer

/**
 * @author Pavel Fatin
 */
private class ScalaLibraryPropertiesEditor(editorComponent: LibraryEditorComponent[ScalaLibraryProperties]) extends LibraryPropertiesEditor {
  private val disposable = Disposer.newDisposable()
  private val chooserDescriptor = new FileChooserDescriptor(true, false, true, false, false, true)
  private val classpathEditor = new PathEditor(chooserDescriptor)
  private val pluginsEditor = new PathEditor(chooserDescriptor)

  def createComponent() = {
    val tabs = new TabbedPaneWrapper(disposable)
    tabs.addTab("Classpath", classpathEditor.createComponent())
    tabs.addTab("Options", new CompilerOptionsTab().getComponent)
    tabs.addTab("Plugins", pluginsEditor.createComponent())

    new ScalaLibraryEditorForm(tabs.getComponent).getComponent
  }

  override def disposeUIResources() {
    Disposer.dispose(disposable)
  }

  def isModified = classpathEditor.isModified

  def reset() {
    val files = properties.compilerClasspath.map(VfsUtil.findFileByIoFile(_, true))
    classpathEditor.resetPath(files.asJava)
  }

  def apply() {
    val roots = classpathEditor.getRoots.toSeq
    properties.compilerClasspath = roots.map(VfsUtilCore.virtualToIoFile)
  }

  private def properties = editorComponent.getProperties
}
