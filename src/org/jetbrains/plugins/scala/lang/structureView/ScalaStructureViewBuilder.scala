package org.jetbrains.plugins.scala
package lang
package structureView

import com.intellij.ide.structureView.{StructureViewModel, TreeBasedStructureViewBuilder}
import com.intellij.openapi.editor.Editor
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.console.ScalaLanguageConsole
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
* @author Alexander.Podkhalyuz
* Date: 04.05.2008
*/

class ScalaStructureViewBuilder(private val myPsiFile: ScalaFile, private val console: ScalaLanguageConsole = null)
  extends TreeBasedStructureViewBuilder {

  @NotNull
  override def createStructureViewModel(editor: Editor): StructureViewModel = {
    new ScalaStructureViewModel(myPsiFile, console)
  }

  override def isRootNodeShown: Boolean = false
}