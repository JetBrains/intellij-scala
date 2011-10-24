package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.PsiElement
import types.result.TypeResult
import types.ScType

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScSelfInvocation extends ScalaPsiElement {
  def args: Option[ScArgumentExprList] = findChild(classOf[ScArgumentExprList])

  def arguments: Seq[ScArgumentExprList] =
    collection.immutable.Seq(findChildrenByClassScala(classOf[ScArgumentExprList]).toSeq: _*)

  def bind: Option[PsiElement]

  def shapeType(i: Int): TypeResult[ScType]

  def shapeMultiType(i: Int): Seq[TypeResult[ScType]]

  def multiType(i: Int): Seq[TypeResult[ScType]]

  def thisElement: PsiElement = getFirstChild
}