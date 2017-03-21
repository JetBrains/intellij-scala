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
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypeAliasSignature}

/**
 * @author Alefas
 * @since 04/04/14.
 */
class ScLightTypeAliasDefinition(s: TypeAliasSignature, val ta: ScTypeAliasDefinition)
  extends LightElement(ta.getManager, ta.getLanguage) with ScTypeAliasDefinition {
  setNavigationElement(ta)

  override def nameId: PsiElement = ta.nameId

  override def upperBound: TypeResult[ScType] = Success(s.upperBound, Some(this))

  override def lowerBound: TypeResult[ScType] = Success(s.lowerBound, Some(this))

  override def aliasedType: TypeResult[ScType] = Success(s.lowerBound, Some(this))

  override def aliasedType(ctx: TypingContext): TypeResult[ScType] = Success(s.lowerBound, Some(this))

  override def aliasedTypeElement: Option[ScTypeElement] = ta.aliasedTypeElement

  override def getOriginalElement: PsiElement = super[ScTypeAliasDefinition].getOriginalElement

  override def toString: String = ta.toString

  override def setModifierProperty(name: String, value: Boolean): Unit = ta.setModifierProperty(name, value)

  override def hasFinalModifier: Boolean = ta.hasFinalModifier

  override def hasAbstractModifier: Boolean = ta.hasAbstractModifier

  override def hasModifierPropertyScala(name: String): Boolean = ta.hasModifierPropertyScala(name)

  override def getModifierList: ScModifierList = ta.getModifierList

  override def getAnnotations: Array[PsiAnnotation] = ta.getAnnotations

  override def getApplicableAnnotations: Array[PsiAnnotation] = ta.getApplicableAnnotations

  override def findAnnotation(qualifiedName: String): PsiAnnotation = ta.findAnnotation(qualifiedName)

  override def addAnnotation(qualifiedName: String): PsiAnnotation = ta.addAnnotation(qualifiedName)

  override def hasAnnotation(qualifiedName: String): Boolean = ta.hasAnnotation(qualifiedName)

  override def annotations: Seq[ScAnnotation] = ta.annotations

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
