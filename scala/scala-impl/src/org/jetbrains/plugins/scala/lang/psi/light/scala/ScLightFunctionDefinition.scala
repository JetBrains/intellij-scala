package org.jetbrains.plugins.scala
package lang.psi.light.scala

import com.intellij.psi._
import com.intellij.psi.impl.light.LightElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAnnotation, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.light.LightUtil
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.result._

/**
 * @author Alefas
 * @since 03/04/14.
 */
class ScLightFunctionDefinition(pTypes: Seq[Seq[ScType]], tParams: Seq[TypeParameter], rt: ScType,
                                val fun: ScFunctionDefinition)
  extends LightElement(fun.getManager, fun.getLanguage) with ScFunctionDefinition {

  override def typeParametersClause: Option[ScTypeParamClause] = fun.typeParametersClause.map(new ScLightTypeParamClause(tParams, _))

  override def paramClauses: ScParameters = new ScLightParameters(pTypes, fun)

  override protected def returnTypeInner: TypeResult = Right(rt)

  override def definedReturnType: TypeResult = Right(rt)

  override def declaredType: TypeResult = Right(rt)

  override def hasExplicitType: Boolean = true

  override def hasFinalModifier: Boolean = fun.hasFinalModifier

  override def hasAbstractModifier: Boolean = fun.hasAbstractModifier

  override def hasModifierPropertyScala(name: String): Boolean = fun.hasModifierPropertyScala(name)

  override def getModifierList: ScModifierList = fun.getModifierList

  override def returnTypeElement: Option[ScTypeElement] = fun.returnTypeElement

  override def name: String = fun.name

  override def toString: String = fun.toString

  override def nameId: PsiElement = fun.nameId

  override def removeAssignment(): Unit = throw new UnsupportedOperationException("Operation on light function")

  override def assignment: Option[PsiElement] = fun.assignment

  override def hasAssign: Boolean = fun.hasAssign

  override def body: Option[ScExpression] = fun.body

  override def psiAnnotations: Array[PsiAnnotation] = fun.getAnnotations

  override def getApplicableAnnotations: Array[PsiAnnotation] = fun.getApplicableAnnotations

  override def findAnnotation(qualifiedName: String): PsiAnnotation = fun.findAnnotation(qualifiedName)

  override def addAnnotation(qualifiedName: String): PsiAnnotation = fun.addAnnotation(qualifiedName)

  override def hasAnnotation(qualifiedName: String): Boolean = fun.hasAnnotation(qualifiedName)

  override def hasParameterClause: Boolean = fun.hasParameterClause

  override def annotations: Seq[ScAnnotation] = fun.annotations

  override def getNavigationElement: PsiElement = LightUtil.originalNavigationElement(fun)

  override def navigate(requestFocus: Boolean): Unit = fun.navigate(requestFocus)

  override def canNavigate: Boolean = fun.canNavigate

  override def canNavigateToSource: Boolean = fun.canNavigateToSource

  override protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    throw new UnsupportedOperationException("Operation on light function")

  override protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T =
    throw new UnsupportedOperationException("Operation on light function")
}
