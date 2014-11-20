package org.jetbrains.plugins.scala
package lang.psi.light.scala

import com.intellij.psi.impl.light.LightElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
 * @author Alefas
 * @since 03/04/14.
 */
class ScLightParameterClause(types: List[ScType], clause: ScParameterClause)
  extends LightElement(clause.getManager, clause.getLanguage) with ScParameterClause {
  override def isImplicit: Boolean = clause.isImplicit

  override def parameters: Seq[ScParameter] = clause.parameters.zip(types).zipWithIndex.map {
    case ((param, tp), i) => new ScLightParameter(param, tp, i)
  }

  override def effectiveParameters: Seq[ScParameter] = parameters

  override def toString: String = "Light parameter clause"

  override def addParameter(param: ScParameter): ScParameterClause =
    throw new UnsupportedOperationException("Operation on light element")

  override protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    throw new UnsupportedOperationException("Operation on light element")

  override protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T =
    throw new UnsupportedOperationException("Operation on light element")
}
