package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base._
import com.intellij.psi._

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScReferenceExpression extends ScalaPsiElement with ScExpression with ScReferenceElement{
  def qualifier : ScExpression

  def stable() : Boolean = qualifier match {
    case null => true
    case q : ScReferenceExpression => q.stable
    case _ => false
  }
}