package org.jetbrains.plugins.scala
package lang.psi.light.scala

import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.{PsiAnnotation, PsiElement, PsiTypeElement}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAnnotation, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult}

/**
 * @author Alefas
 * @since 03/04/14.
 */
class ScLightParameter(val param: ScParameter, tp: ScType, i: Int)
  extends LightElement(param.getManager, param.getLanguage) with ScParameter {
  override def nameId: PsiElement = param.nameId

  override def getType(ctx: TypingContext.type): TypeResult[ScType] = Success(tp, Some(this))

  override def deprecatedName: Option[String] = param.deprecatedName

  override def getActualDefaultExpression: Option[ScExpression] = param.getActualDefaultExpression

  override def baseDefaultParam: Boolean = param.baseDefaultParam

  override def isCallByNameParameter: Boolean = param.isCallByNameParameter

  override def isRepeatedParameter: Boolean = param.isRepeatedParameter

  override def typeElement: Option[ScTypeElement] = param.typeElement

  override def getTypeElement: PsiTypeElement = param.getTypeElement

  override def toString: String = param.toString

  override def index: Int = i

  override def getModifierList: ScModifierList = param.getModifierList

  override def hasModifierProperty(name: String): Boolean = param.hasModifierProperty(name)

  override def hasModifierPropertyScala(name: String): Boolean = param.hasModifierPropertyScala(name)

  override def hasAbstractModifier: Boolean = param.hasAbstractModifier

  override def hasFinalModifier: Boolean = param.hasFinalModifier

  override def psiAnnotations: Array[PsiAnnotation] = param.getAnnotations

  override def getApplicableAnnotations: Array[PsiAnnotation] = param.getApplicableAnnotations

  override def findAnnotation(qualifiedName: String): PsiAnnotation = param.findAnnotation(qualifiedName)

  override def addAnnotation(qualifiedName: String): PsiAnnotation =
    throw new UnsupportedOperationException("Operation on light element")

  override def hasAnnotation(qualifiedName: String): Boolean = param.hasAnnotation(qualifiedName)

  override def annotations: Seq[ScAnnotation] = param.annotations

  override def setModifierProperty(name: String, value: Boolean): Unit =
    throw new UnsupportedOperationException("Operation on light element")

  override protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    throw new UnsupportedOperationException("Operation on light element")

  override protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T =
    throw new UnsupportedOperationException("Operation on light element")
}
