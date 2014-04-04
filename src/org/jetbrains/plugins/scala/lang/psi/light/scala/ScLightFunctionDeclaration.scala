package org.jetbrains.plugins.scala
package lang.psi.light.scala

import com.intellij.psi.impl.light.LightElement
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDeclaration
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult}
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation

/**
 * @author Alefas
 * @since 03/04/14.
 */
class ScLightFunctionDeclaration(pTypes: List[List[ScType]], tParams: List[TypeParameter], rt: ScType,
                                 val fun: ScFunctionDeclaration)
  extends LightElement(fun.getManager, fun.getLanguage) with ScFunctionDeclaration {
  setNavigationElement(fun)

  override def typeParametersClause: Option[ScTypeParamClause] = fun.typeParametersClause.map(new ScLightTypeParamClause(tParams, _))

  override def paramClauses: ScParameters = new ScLightParameters(pTypes, fun)

  override def returnTypeInner: TypeResult[ScType] = Success(rt, Some(this))

  override def definedReturnType: TypeResult[ScType] = Success(rt, Some(this))

  override def declaredType: TypeResult[ScType] = Success(rt, Some(this))

  override def hasExplicitType: Boolean = true

  override def hasFinalModifier: Boolean = fun.hasFinalModifier

  override def hasAbstractModifier: Boolean = fun.hasAbstractModifier

  override def hasModifierPropertyScala(name: String): Boolean = fun.hasModifierPropertyScala(name)

  override def getModifierList: ScModifierList = fun.getModifierList

  override def returnTypeElement: Option[ScTypeElement] = fun.returnTypeElement

  override def name: String = fun.name

  override def toString: String = fun.toString

  override def nameId: PsiElement = fun.nameId

  override def hasAssign: Boolean = fun.hasAssign

  override def getAnnotations: Array[PsiAnnotation] = fun.getAnnotations

  override def getApplicableAnnotations: Array[PsiAnnotation] = fun.getApplicableAnnotations

  override def findAnnotation(qualifiedName: String): PsiAnnotation = fun.findAnnotation(qualifiedName)

  override def addAnnotation(qualifiedName: String): PsiAnnotation = fun.addAnnotation(qualifiedName)

  override def hasAnnotation(qualifiedName: String): Option[ScAnnotation] = fun.hasAnnotation(qualifiedName)

  override def hasAnnotation(clazz: PsiClass): Boolean = fun.hasAnnotation(clazz)

  override def annotationNames: Seq[String] = fun.annotationNames

  override def annotations: Seq[ScAnnotation] = fun.annotations

  override def navigate(requestFocus: Boolean): Unit = fun.navigate(requestFocus)

  override def canNavigate: Boolean = fun.canNavigate

  override def canNavigateToSource: Boolean = fun.canNavigateToSource

  override protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    throw new UnsupportedOperationException("Operation on light function")

  override protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T =
    throw new UnsupportedOperationException("Operation on light function")
}
