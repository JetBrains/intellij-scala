package org.jetbrains.plugins.scala.lang
package psi
package light.scala

import com.intellij.psi.{PsiAnnotation, PsiElement, PsiIdentifier, PsiTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAnnotation, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
 * @author Alefas
 * @since 03/04/14.
 */
final class ScLightParameter(override protected val delegate: ScParameter,
                             override val index: Int)
                            (implicit private val parameterType: ScType)
  extends ScLightElement(delegate) with ScParameter {

  override def getNavigationElement: PsiElement = super.getNavigationElement

  override def getNameIdentifier: PsiIdentifier = super.getNameIdentifier

  override def `type`() = Right(parameterType)

  override def deprecatedName: Option[String] = delegate.deprecatedName

  override def getActualDefaultExpression: Option[ScExpression] = delegate.getActualDefaultExpression

  override def baseDefaultParam: Boolean = delegate.baseDefaultParam

  override def isCallByNameParameter: Boolean = delegate.isCallByNameParameter

  override def isRepeatedParameter: Boolean = delegate.isRepeatedParameter

  override def typeElement: Option[ScTypeElement] = delegate.typeElement

  override def getTypeElement: PsiTypeElement = delegate.getTypeElement

  override def getModifierList: ScModifierList = delegate.getModifierList

  override def hasModifierProperty(name: String): Boolean = delegate.hasModifierProperty(name)

  override def psiAnnotations: Array[PsiAnnotation] = delegate.getAnnotations

  override def getApplicableAnnotations: Array[PsiAnnotation] = delegate.getApplicableAnnotations

  override def findAnnotation(qualifiedName: String): PsiAnnotation = delegate.findAnnotation(qualifiedName)

  override def addAnnotation(qualifiedName: String): PsiAnnotation =
    throw new UnsupportedOperationException("Operation on light element")

  override def hasAnnotation(qualifiedName: String): Boolean = delegate.hasAnnotation(qualifiedName)

  override def annotations: Seq[ScAnnotation] = delegate.annotations
}
