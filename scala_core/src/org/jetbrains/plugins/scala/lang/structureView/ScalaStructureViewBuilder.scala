package org.jetbrains.plugins.scala.lang.structureView

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.lang.psi._

/**
* @author Alexander.Podkhalyuz
* Date: 04.05.2008
*/

class ScalaStructureViewBuilder(private val myPsiFile: ScalaFile) extends TreeBasedStructureViewBuilder {
  @NotNull
  def createStructureViewModel(): StructureViewModel = {
    return new ScalaStructureViewModel(myPsiFile)
  }
}