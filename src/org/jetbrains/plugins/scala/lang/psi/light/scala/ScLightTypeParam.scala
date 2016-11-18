package org.jetbrains.plugins.scala
package lang.psi.light.scala

import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.{PsiAnnotation, PsiElement, PsiTypeParameterListOwner}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.PsiClassFake
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult}

/**
 * @author Alefas
 * @since 03/04/14.
 */
class ScLightTypeParam(t: TypeParameter, val tParam: ScTypeParam)
  extends LightElement(tParam.getManager, tParam.getLanguage) with ScTypeParam with PsiClassFake {
  override def nameId: PsiElement = tParam.nameId

  override def upperBound: TypeResult[ScType] = Success(t.upperType.v, Some(this))

  override def lowerBound: TypeResult[ScType] = Success(t.lowerType.v, Some(this))

  override def getIndex: Int = tParam.getIndex

  override def getOwner: PsiTypeParameterListOwner = tParam.getOwner

  override def addAnnotation(qualifiedName: String): PsiAnnotation =
    throw new UnsupportedOperationException("Operation on light element")

  override def findAnnotation(qualifiedName: String): PsiAnnotation = tParam.findAnnotation(qualifiedName)

  override def getApplicableAnnotations: Array[PsiAnnotation] = tParam.getApplicableAnnotations

  override def getAnnotations: Array[PsiAnnotation] = tParam.getAnnotations

  override def typeParameterText: String = tParam.typeParameterText

  override def getContainingFileName: String = tParam.getContainingFileName

  override def getOffsetInFile: Int = tParam.getOffsetInFile

  override def owner: ScTypeParametersOwner = tParam.owner

  override def isContravariant: Boolean = tParam.isContravariant

  override def isCovariant: Boolean = tParam.isCovariant

  override def toString: String = tParam.toString

  override def typeParameters: Seq[ScTypeParam] = t.typeParameters.zip(tParam.typeParameters).map {
    case (t: TypeParameter, tParam: ScTypeParam) => new ScLightTypeParam(t, tParam)
  }

  override protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    throw new UnsupportedOperationException("Operation on light element")

  override protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T =
    throw new UnsupportedOperationException("Operation on light element")

  override def isHigherKindedTypeParameter: Boolean = tParam.isHigherKindedTypeParameter
}
