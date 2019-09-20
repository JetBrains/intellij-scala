package org.jetbrains.plugins.scala.lang.psi.uast.expressions

import java.util

import com.intellij.psi.{PsiElement, PsiMethod, PsiType}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.{ScUAnnotated, ScUElement, ScUMultiResolvable}
import org.jetbrains.plugins.scala.lang.psi.uast.declarations.ScUErrorClass
import org.jetbrains.plugins.scala.lang.psi.uast.internals.{LazyUElement, ResolveCommon}
import org.jetbrains.uast.{UAnonymousClass, UClass, UExpression, UObjectLiteralExpression, UObjectLiteralExpressionAdapter, UReferenceExpression}

import scala.collection.JavaConverters._

/**
  * [[ScNewTemplateDefinition]] adapter for the [[UObjectLiteralExpression]]
  *
  * @param scElement Scala PSI expression representing anonymous class definition,
  *                  e.g. `new Trait {...}`
  */
class ScUObjectLiteralExpression(
  override protected val scElement: ScNewTemplateDefinition,
  override protected val parent: LazyUElement
) extends UObjectLiteralExpressionAdapter
    with ScUElement
    with ScUAnnotated
    with ScUMultiResolvable {

  override type PsiFacade = PsiElement

  override protected def scReference: Option[ScReference] =
    scElement.constructorInvocation.flatMap(_.reference)

  override def getDeclaration: UClass =
    scElement.extendsBlock.templateBody
      .flatMap(_.convertTo[UAnonymousClass](this))
      .getOrElse(new ScUErrorClass(scElement, LazyUElement.just(this)))

  private val uConstructor: Option[ScUConstructorCallExpression] =
    scElement.constructorInvocation.map(
      new ScUConstructorCallExpression(_, LazyUElement.Empty)
    )

  @Nullable
  override def getClassReference: UReferenceExpression =
    uConstructor.map(_.getClassReference).orNull

  override def getTypeArgumentCount: Int =
    uConstructor.map(_.getTypeArgumentCount).getOrElse(0)

  override def getTypeArguments: util.List[PsiType] =
    uConstructor
      .map(_.getTypeArguments)
      .getOrElse(Seq.empty.asJava)

  override def getValueArgumentCount: Int =
    uConstructor.map(_.getValueArgumentCount).getOrElse(0)

  override def getValueArguments: util.List[UExpression] =
    uConstructor
      .map(_.getValueArguments)
      .getOrElse(Seq.empty.asJava)

  @Nullable
  override def getArgumentForParameter(i: Int): UExpression =
    uConstructor.map(_.getArgumentForParameter(i)).orNull

  @Nullable
  override def resolve(): PsiMethod =
    ResolveCommon.resolveNullable[PsiMethod](scReference)
}
