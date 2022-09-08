package org.jetbrains.plugins.scala.testingSupport

import com.intellij.ide.structureView.{StructureViewModel, TreeBasedStructureViewBuilder}
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.console.ScalaLanguageConsole
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

class TestStructureViewBuilder(file: ScalaFile, console: Option[ScalaLanguageConsole] = None) extends TreeBasedStructureViewBuilder {
  override def createStructureViewModel(editor: Editor): StructureViewModel = new TestStructureViewModel(file, console)

  override def isRootNodeShown: Boolean = false
}
