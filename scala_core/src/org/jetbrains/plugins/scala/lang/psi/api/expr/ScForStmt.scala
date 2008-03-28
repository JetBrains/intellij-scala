package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.PsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScForStmt extends ScExpression {

  def isEnumerators = (e: PsiElement) => e.isInstanceOf[ScEnumerators]
  def enumerators: ScEnumerators = childSatisfyPredicateForPsiElement(isEnumerators).asInstanceOf[ScEnumerators]


}