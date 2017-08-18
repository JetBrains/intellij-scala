package org.jetbrains.plugins.scala
package lang.psi.light.scala

import com.intellij.psi.impl.light.LightElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameterClause, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
 * @author Alefas
 * @since 03/04/14.
 */
class ScLightParameters(pTypes: Seq[Seq[ScType]], p: ScFunction)
  extends LightElement(p.getManager, p.getLanguage) with ScParameters {
  override def clauses: Seq[ScParameterClause] =
    pTypes.zip(p.effectiveParameterClauses).map {
      case (types: List[ScType], clause: ScParameterClause) => new ScLightParameterClause(types, clause)
    }

  override def toString: String = "Light parameters"

  override protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    throw new UnsupportedOperationException("Operation on light parameters")

  override protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T =
    throw new UnsupportedOperationException("Operation on light parameters")
}
