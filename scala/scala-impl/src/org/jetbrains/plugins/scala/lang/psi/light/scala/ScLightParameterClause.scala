package org.jetbrains.plugins.scala.lang
package psi
package light.scala

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.light.LightElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
 * @author Alefas
 * @since 03/04/14.
 */
final class ScLightParameterClause(types: Seq[ScType], clause: ScParameterClause)
  extends LightElement(clause.getManager, clause.getLanguage) with ScParameterClause {

  override def isImplicit: Boolean = clause.isImplicit

  override def parameters: Seq[ScParameter] = clause
    .parameters
    .zipWithIndex
    .zip(types)
    .map {
      case ((parameter, index), returnType) => new ScLightParameter(parameter, index)(returnType)
    }

  override def effectiveParameters: Seq[ScParameter] = parameters

  override def toString: String = "Light parameter clause"

  override def addParameter(param: ScParameter): ScParameterClause =
    throw new UnsupportedOperationException("Operation on light element")

  override protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    throw new UnsupportedOperationException("Operation on light element")

  override protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T =
    throw new UnsupportedOperationException("Operation on light element")

  override def owner: PsiElement =
    ScalaPsiUtil.getContextOfType(this, true, classOf[ScFunctionExpr], classOf[ScFunction], classOf[ScPrimaryConstructor])
}
