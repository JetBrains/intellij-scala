package org.jetbrains.plugins.scala.lang.psi.api.expr.util
/**
* @author ilyas
*/

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.PsiElement

trait ScConditional extends ScalaPsiElement {

  def isCondition = (e: PsiElement) => {
    e.isInstanceOf[ScExpression] &&
    {
      val parent = e.getParent
      val last = for (val child <- parent.getChildren; child.isInstanceOf[ScExpression]) child
      parent != last
    }
  }

  def condition: ScExpression = childSatisfyPredicateForPsiElement(isCondition).asInstanceOf[ScExpression]

}