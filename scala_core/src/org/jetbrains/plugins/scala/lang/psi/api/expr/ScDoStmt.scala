package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import expr.util._
import com.intellij.psi._

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScDoStmt extends ScExpression with ScConditional {
  override def isCondition = (e: PsiElement) => {
    e.isInstanceOf[ScExpression] &&
    {
      val parent = e.getParent
      val last = for (child <- parent.getChildren; if child.isInstanceOf[ScExpression]) child
      parent == last
    }
  }
}