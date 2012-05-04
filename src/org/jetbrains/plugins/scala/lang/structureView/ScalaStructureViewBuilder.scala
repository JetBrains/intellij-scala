package org.jetbrains.plugins.scala
package lang
package structureView

import com.intellij.ide.structureView.StructureViewModel
import psi.api.ScalaFile;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;

import org.jetbrains.annotations.NotNull
/**
* @author Alexander.Podkhalyuz
* Date: 04.05.2008
*/

class ScalaStructureViewBuilder(private val myPsiFile: ScalaFile) extends TreeBasedStructureViewBuilder {
  @NotNull
  def createStructureViewModel(): StructureViewModel = {
    new ScalaStructureViewModel(myPsiFile)
  }

  override def isRootNodeShown: Boolean = false
}