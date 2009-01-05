package org.jetbrains.plugins.scala.lang.structureView

import com.intellij.ide.structureView.StructureViewModel
import psi._
import psi.api.ScalaFile;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull
/**
* @author Alexander.Podkhalyuz
* Date: 04.05.2008
*/

class ScalaStructureViewBuilder(private val myPsiFile: ScalaFile) extends TreeBasedStructureViewBuilder {
  @NotNull
  def createStructureViewModel(): StructureViewModel = {
    return new ScalaStructureViewModel(myPsiFile)
  }

  override def isRootNodeShown: Boolean = false
}