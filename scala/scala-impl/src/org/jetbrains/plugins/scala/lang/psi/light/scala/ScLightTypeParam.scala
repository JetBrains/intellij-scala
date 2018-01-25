package org.jetbrains.plugins.scala
package lang.psi.light.scala

import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.{PsiAnnotation, PsiElement, PsiTypeParameterListOwner}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.PsiClassFake
import org.jetbrains.plugins.scala.lang.psi.types.api
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.project.ProjectContext

/**
 * @author Alefas
 * @since 03/04/14.
 */
trait ScLightTypeParam extends ScTypeParam with PsiClassFake

class ScDelegatingLightTypeParam(t: TypeParameter, val tParam: ScTypeParam)
  extends LightElement(tParam.getManager, tParam.getLanguage) with ScLightTypeParam {

  override def nameId: PsiElement = tParam.nameId

  override val typeParamId: Long = tParam.typeParamId

  override def upperBound: TypeResult = Right(t.upperType)

  override def lowerBound: TypeResult = Right(t.lowerType)

  override def getIndex: Int = tParam.getIndex

  override def getOwner: PsiTypeParameterListOwner = tParam.getOwner

  override def addAnnotation(qualifiedName: String): PsiAnnotation =
    throw new UnsupportedOperationException("Operation on light element")

  override def findAnnotation(qualifiedName: String): PsiAnnotation = tParam.findAnnotation(qualifiedName)

  override def getApplicableAnnotations: Array[PsiAnnotation] = tParam.getApplicableAnnotations

  override def psiAnnotations: Array[PsiAnnotation] = tParam.getAnnotations

  override def typeParameterText: String = tParam.typeParameterText

  override def getContainingFileName: String = tParam.getContainingFileName

  override def getOffsetInFile: Int = tParam.getOffsetInFile

  override def owner: ScTypeParametersOwner = tParam.owner

  override def isContravariant: Boolean = tParam.isContravariant

  override def isCovariant: Boolean = tParam.isCovariant

  override def toString: String = tParam.toString

  override def typeParameters: Seq[ScTypeParam] = t.typeParameters.zip(tParam.typeParameters).map {
    case (t: TypeParameter, tParam: ScTypeParam) => new ScDelegatingLightTypeParam(t, tParam)
  }

  override protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    throw new UnsupportedOperationException("Operation on light element")

  override protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T =
    throw new UnsupportedOperationException("Operation on light element")

  override def isHigherKindedTypeParameter: Boolean = tParam.isHigherKindedTypeParameter
}

class ScExistentialLightTypeParam(override val name: String)(implicit pc: ProjectContext)
  extends LightElement(pc, ScalaLanguage.INSTANCE) with ScLightTypeParam {

  override def getIndex: Int = 0

  override def isCovariant: Boolean = false

  override def isContravariant: Boolean = false

  override def getOffsetInFile: Int = -1

  override def typeParameters: Seq[ScTypeParam] = Seq.empty

  override def hasTypeParameters = false

  override def getContainingFileName: String = "No containing file"

  override def typeParameterText: String = name

  override val typeParamId: Long = params.freshTypeParamId(this)

  override def isHigherKindedTypeParameter: Boolean = false

  override def lowerBound: TypeResult = Right(api.Nothing)

  override def upperBound: TypeResult = Right(api.Any)

  override def toString: String = name

  override def owner: ScTypeParametersOwner = notSupported

  override def nameId: PsiElement = notSupported

  override protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T = notSupported

  override protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] = notSupported

  override def getOwner: PsiTypeParameterListOwner = notSupported

  private def notSupported = throw new UnsupportedOperationException("Operation on light existential type parameter")
}
