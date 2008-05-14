package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi._

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

trait ScCaseClause extends ScalaPsiElement {
  def pattern : ScPattern

  override def processDeclarations(processor: PsiScopeProcessor,
      state : ResolveState,
      lastParent: PsiElement,
      place: PsiElement): Boolean = {

    val p = pattern
    if (p != null && p != lastParent) p.processDeclarations (processor, state, lastParent, place)
    else true
  }
}