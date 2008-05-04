package org.jetbrains.plugins.scala.lang.structureView

import com.intellij.lang.PsiStructureViewFactory
import com.intellij.psi.PsiFile
import com.intellij.ide.structureView.StructureViewBuilder
import org.jetbrains.plugins.scala.lang.psi._

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

class ScalaStructureViewFactory extends PsiStructureViewFactory {
  def getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder = new ScalaStructureViewBuilder(psiFile.asInstanceOf[ScalaFile])
}