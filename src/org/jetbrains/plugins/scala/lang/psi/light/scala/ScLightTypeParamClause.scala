package org.jetbrains.plugins.scala
package lang.psi.light.scala

import com.intellij.psi.impl.light.LightElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScTypeParam, ScTypeParamClause}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.TypeParameter

/**
 * @author Alefas
 * @since 03/04/14.
 */
class ScLightTypeParamClause(tParams: List[TypeParameter], t: ScTypeParamClause)
  extends LightElement(t.getManager, t.getLanguage) with ScTypeParamClause {
  override def getTextByStub: String = t.getTextByStub

  override def typeParameters: Seq[ScTypeParam] = tParams.zip(t.typeParameters).map {
    case (t: TypeParameter, tParam: ScTypeParam) => new ScLightTypeParam(t, tParam)
  }

  override def toString: String = t.toString

  override protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    throw new UnsupportedOperationException("Operation on light element")

  override protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T =
    throw new UnsupportedOperationException("Operation on light element")
}
