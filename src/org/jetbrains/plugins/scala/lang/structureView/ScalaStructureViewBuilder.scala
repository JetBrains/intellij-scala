package org.jetbrains.plugins.scala
package lang
package structureView

import com.intellij.ide.structureView.StructureViewModel
import psi.api.ScalaFile
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder

import org.jetbrains.annotations.NotNull
import console.ScalaLanguageConsole

/**
* @author Alexander.Podkhalyuz
* Date: 04.05.2008
*/

class ScalaStructureViewBuilder(private val myPsiFile: ScalaFile, private val console: ScalaLanguageConsole = null)
  extends TreeBasedStructureViewBuilder {

  @NotNull
  def createStructureViewModel(): StructureViewModel = {
    new ScalaStructureViewModel(myPsiFile, console)
  }

  override def isRootNodeShown: Boolean = false
}