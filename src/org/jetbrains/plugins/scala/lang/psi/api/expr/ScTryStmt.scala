package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.tree._
import lang.parser.ScalaElementTypes


/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 06.03.2008
* Time: 18:49:57
* To change this template use File | Settings | File Templates.
*/

trait ScTryStmt extends ScExpression {

  def isTryBlock = (e: IElementType) => ScalaElementTypes.TRY_BLOCK.equals(e)
  def tryBlock = childSatisfyPredicateForElementType(isTryBlock).asInstanceOf[ScTryBlock]

  def isCatchBlock = (e: IElementType) => ScalaElementTypes.CATCH_BLOCK.equals(e)
  def catchBlock = childSatisfyPredicateForElementType(isCatchBlock).asInstanceOf[ScCatchBlock]


}