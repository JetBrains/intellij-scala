package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.psi.impl.PsiVariableEx
import com.intellij.psi.impl.light.{LightFieldBuilder, LightIdentifier}
import com.intellij.psi.{JavaElementVisitor, JavaResolveResult, PsiElement, PsiElementVisitor, PsiEnumConstant, PsiEnumConstantInitializer, PsiExpression, PsiExpressionList, PsiIdentifier, PsiMethod, PsiModifier, PsiVariable}
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumSingletonCase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScEnum
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType

import java.util

final class ScLightEnumConstant(
  override val delegate: ScEnumSingletonCase,
  enumClass: ScEnum
) extends LightFieldBuilder(delegate.name, ScDesignatorType(enumClass).toPsiType, delegate)
    with PsiEnumConstant
    with PsiVariableEx
    with NavigablePsiElementWrapper[ScEnumSingletonCase] {

  setContainingClass(enumClass)
  setModifiers(PsiModifier.PUBLIC, PsiModifier.STATIC, PsiModifier.FINAL)

  override def accept(visitor: PsiElementVisitor): Unit = visitor match {
    case javaVisitor: JavaElementVisitor => javaVisitor.visitEnumConstant(this)
    case _                               => visitor.visitElement(this)
  }

  override def getNameIdentifier: PsiIdentifier =
    new LightIdentifier(getManager, getName)

  override def getArgumentList: PsiExpressionList = null

  override def getInitializingClass: PsiEnumConstantInitializer = null

  override def getOrCreateInitializingClass: PsiEnumConstantInitializer =
    throw new IncorrectOperationException("Cannot create an initializing class for a Scala enum case")

  override def resolveMethod(): PsiMethod = null

  override def resolveMethodGenerics(): JavaResolveResult = JavaResolveResult.EMPTY

  override def resolveConstructor(): PsiMethod = null

  override def hasInitializer: Boolean = true

  override def getInitializer: PsiExpression = null

  override def setInitializer(initializer: PsiExpression): Unit =
    throw new IncorrectOperationException("Cannot replace the initializer of a Scala enum case")

  override def computeConstantValue(): AnyRef = this

  override def computeConstantValue(visitedVars: util.Set[PsiVariable]): AnyRef = this

  override def copy(): PsiElement = new ScLightEnumConstant(delegate, enumClass)

  override def toString: String = s"ScLightEnumConstant:$getName"
}
