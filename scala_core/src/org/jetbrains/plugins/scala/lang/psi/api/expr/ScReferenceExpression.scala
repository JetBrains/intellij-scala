package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base._
import com.intellij.psi._

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScReferenceExpression extends ScalaPsiElementImpl with ScExpression with ScReferenceElement{
  /*
   * @return expression qualifier
   */
  def getQualifier() = findChildByClass(classOf[ScExpression])
}