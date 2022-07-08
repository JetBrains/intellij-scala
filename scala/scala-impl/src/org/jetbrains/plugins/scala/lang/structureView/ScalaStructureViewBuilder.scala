package org.jetbrains.plugins.scala.lang.structureView

import com.intellij.ide.structureView.{StructureViewModel, TreeBasedStructureViewBuilder}
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.console.ScalaLanguageConsole
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

class ScalaStructureViewBuilder(file: ScalaFile, console: Option[ScalaLanguageConsole] = None) extends TreeBasedStructureViewBuilder {
  override def createStructureViewModel(editor: Editor): StructureViewModel = new ScalaStructureViewModel(file, console)

  override def isRootNodeShown: Boolean = false
}