package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.{PsiElement, PsiReference}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScSelfInvocation extends ScalaPsiElement with PsiReference {
  def args: Option[ScArgumentExprList] = findChild(classOf[ScArgumentExprList])

  def arguments: Seq[ScArgumentExprList] = findChildrenByClassScala(classOf[ScArgumentExprList]).toSeq

  def bind: Option[PsiElement]

  def shapeType(i: Int): TypeResult[ScType]

  def shapeMultiType(i: Int): Seq[TypeResult[ScType]]

  def multiType(i: Int): Seq[TypeResult[ScType]]

  def thisElement: PsiElement = getFirstChild
}