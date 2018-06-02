package org.jetbrains.plugins.scala
package lang.psi.light.scala

import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.{PsiAnnotation, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParamClause
import org.jetbrains.plugins.scala.lang.psi.light.LightUtil
import org.jetbrains.plugins.scala.lang.psi.types.TypeAliasSignature
import org.jetbrains.plugins.scala.lang.psi.types.result._

/**
 * @author Alefas
 * @since 04/04/14.
 */
class ScLightTypeAliasDefinition(s: TypeAliasSignature, val ta: ScTypeAliasDefinition)
  extends LightElement(ta.getManager, ta.getLanguage) with ScTypeAliasDefinition {

  override def nameId: PsiElement = ta.nameId

  override def upperBound: TypeResult = Right(s.upperBound)

  override def lowerBound: TypeResult = Right(s.lowerBound)

  override def aliasedType: TypeResult = Right(s.lowerBound)

  override def aliasedTypeElement: Option[ScTypeElement] = ta.aliasedTypeElement

  override def getOriginalElement: PsiElement = super[ScTypeAliasDefinition].getOriginalElement

  override def toString: String = ta.toString

  override def setModifierProperty(name: String, value: Boolean): Unit = ta.setModifierProperty(name, value)

  override def hasFinalModifier: Boolean = ta.hasFinalModifier

  override def hasAbstractModifier: Boolean = ta.hasAbstractModifier

  override def hasModifierPropertyScala(name: String): Boolean = ta.hasModifierPropertyScala(name)

  override def getModifierList: ScModifierList = ta.getModifierList

  override def psiAnnotations: Array[PsiAnnotation] = ta.getAnnotations

  override def getApplicableAnnotations: Array[PsiAnnotation] = ta.getApplicableAnnotations

  override def findAnnotation(qualifiedName: String): PsiAnnotation = ta.findAnnotation(qualifiedName)

  override def addAnnotation(qualifiedName: String): PsiAnnotation = ta.addAnnotation(qualifiedName)

  override def hasAnnotation(qualifiedName: String): Boolean = ta.hasAnnotation(qualifiedName)

  override def annotations: Seq[ScAnnotation] = ta.annotations

  override def getNavigationElement: PsiElement = LightUtil.originalNavigationElement(ta)

  override def navigate(requestFocus: Boolean): Unit = ta.navigate(requestFocus)

  override def canNavigate: Boolean = ta.canNavigate

  override def canNavigateToSource: Boolean = ta.canNavigateToSource

  override def typeParametersClause: Option[ScTypeParamClause] =
    ta.typeParametersClause.map(new ScLightTypeParamClause(s.typeParams, _))

  override protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    throw new UnsupportedOperationException("Operation on light element")

  override protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T =
    throw new UnsupportedOperationException("Operation on light element")
}
