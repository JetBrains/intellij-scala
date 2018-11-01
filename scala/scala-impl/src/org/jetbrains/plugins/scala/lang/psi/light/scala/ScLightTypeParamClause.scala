package org.jetbrains.plugins.scala
package lang.psi.light.scala

import com.intellij.psi.impl.light.LightElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScTypeParam, ScTypeParamClause}
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter

/**
  * @author Alefas
  * @since 03/04/14.
  */
final class ScLightTypeParamClause(private val delegate: ScTypeParamClause)
                                  (implicit parameters: Seq[TypeParameter])
  extends LightElement(delegate.getManager, delegate.getLanguage) with ScTypeParamClause {

  override def getTextByStub: String = delegate.getTextByStub

  override def typeParameters: Seq[ScTypeParam] = parameters
    .zip(delegate.typeParameters)
    .map {
      case (typeParameter, psiTypeParameter) => new ScDelegatingLightTypeParam(typeParameter, psiTypeParameter)
    }

  override def toString: String = delegate.toString

  override protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    throw new UnsupportedOperationException("Operation on light element")

  override protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T =
    throw new UnsupportedOperationException("Operation on light element")
}