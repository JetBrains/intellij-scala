package org.jetbrains.plugins.scala.lang.structureView

import com.intellij.lang.PsiStructureViewFactory
import com.intellij.psi.PsiFile
import com.intellij.ide.structureView.StructureViewBuilder
import psi._
import psi.api.ScalaFile

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

class ScalaStructureViewFactory extends PsiStructureViewFactory {
  def getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder = new ScalaStructureViewBuilder(psiFile.asInstanceOf[ScalaFile])
}