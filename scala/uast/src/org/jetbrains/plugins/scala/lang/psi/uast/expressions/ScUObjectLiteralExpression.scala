package org.jetbrains.plugins.scala.lang.psi.uast.expressions

import java.{util => ju}

import com.intellij.psi.{PsiElement, PsiMethod, PsiType}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.{ScUAnnotated, ScUElement, ScUMultiResolvable}
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.declarations.ScUErrorClass
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.plugins.scala.uast.ReferenceExt
import org.jetbrains.uast.{UAnonymousClass, UClass, UExpression, UObjectLiteralExpression, UObjectLiteralExpressionAdapter, UReferenceExpression}

/**
 * [[ScNewTemplateDefinition]] adapter for the [[UObjectLiteralExpression]]
 *
 * @param scElement Scala PSI expression representing anonymous class definition,
 *                  e.g. `new Trait {...}`
 */
final class ScUObjectLiteralExpression(
  override protected val scElement: ScNewTemplateDefinition,
  override protected val parent: LazyUElement
) extends UObjectLiteralExpressionAdapter
    with ScUElement
    with ScUAnnotated
    with ScUMultiResolvable {

  override type PsiFacade = PsiElement

  override protected def scReference: Option[ScReference] =
    scElement.firstConstructorInvocation.flatMap(_.reference)

  override def getDeclaration: UClass = {
    val extendsBlock = scElement.extendsBlock
    val anonymousClass = extendsBlock.convertTo[UAnonymousClass](this)
    anonymousClass.getOrElse(new ScUErrorClass(scElement, LazyUElement.just(this)))
  }

  private val uConstructor: Option[ScUConstructorCallExpression] =
    scElement.firstConstructorInvocation.map(
      new ScUConstructorCallExpression(_, LazyUElement.Empty)
    )

  @Nullable
  override def getClassReference: UReferenceExpression =
    uConstructor.map(_.getClassReference).orNull

  override def getTypeArgumentCount: Int =
    uConstructor.map(_.getTypeArgumentCount).getOrElse(0)

  override def getTypeArguments: ju.List[PsiType] =
    uConstructor
      .map(_.getTypeArguments)
      .getOrElse(ju.Collections.emptyList())

  override def getValueArgumentCount: Int =
    uConstructor.map(_.getValueArgumentCount).getOrElse(0)

  override def getValueArguments: ju.List[UExpression] =
    uConstructor
      .map(_.getValueArguments)
      .getOrElse(ju.Collections.emptyList())

  @Nullable
  override def getArgumentForParameter(i: Int): UExpression =
    uConstructor.map(_.getArgumentForParameter(i)).orNull

  @Nullable
  override def resolve(): PsiMethod =
    scReference.map(_.resolveTo[PsiMethod]).orNull
}
