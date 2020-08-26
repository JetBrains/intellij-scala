package org.jetbrains.plugins.scala.lang.psi.uast.expressions

import java.util

import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.{PsiClass, PsiElement, PsiMethod, PsiType}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructorInvocation, ScReference}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.{ScUAnnotated, ScUElement, ScUMultiResolvable}
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.plugins.scala.uast.ReferenceExt
import org.jetbrains.uast.{UCallExpression, UCallExpressionAdapter, UExpression, UIdentifier, UReferenceExpression, UastCallKind}

import scala.jdk.CollectionConverters._

/**
 * [[ScConstructorInvocation]] adapter for the [[UCallExpression]]
 * with kind [[UastCallKind.CONSTRUCTOR_CALL]]
 *
 * @param scElement Scala PSI element representing constructor invocation
 */
final class ScUConstructorCallExpression(
  override protected val scElement: ScConstructorInvocation,
  override protected val parent: LazyUElement
) extends UCallExpressionAdapter
    with ScUElement
    with ScUAnnotated
    with ScUMultiResolvable {

  override type PsiFacade = PsiElement

  @Nullable
  override def getSourcePsi: PsiElement =
    scElement.newTemplate.getOrElse(scElement)

  @Nullable
  override def getClassReference: UReferenceExpression =
    scReference.flatMap(_.convertTo[UReferenceExpression](this)).orNull

  override def getKind: UastCallKind = UastCallKind.CONSTRUCTOR_CALL

  @Nullable
  override def getMethodIdentifier: UIdentifier = null

  @Nullable
  override def getMethodName: String = null

  @Nullable
  override def getReceiver: UExpression = null

  @Nullable
  override def getReceiverType: PsiType = null

  @Nullable
  override def getReturnType: PsiType =
    scElement.reference
      .map(_.resolve())
      .flatMap {
        case psiClass: PsiClass => Option(PsiTypesUtil.getClassType(psiClass))
        case _                  => None
      }
      .getOrElse(
        scElement.typeElement.`type`().map(_.toPsiType).getOrElse(null)
      )

  override def getTypeArgumentCount: Int =
    scElement.typeArgList.map(_.getArgsCount).getOrElse(0)

  override def getTypeArguments: util.List[PsiType] =
    scElement.typeArgList
      .map(_.typeArgs.flatMap(_.`type`().map(_.toPsiType).toOption))
      .getOrElse(Seq.empty)
      .asJava

  override def getValueArgumentCount: Int =
    scElement.arguments.map(_.exprs.size).sum

  override def getValueArguments: util.List[UExpression] = {
    Seq.concat(
      scElement.arguments
        .map(_.exprs.map(_.convertToUExpressionOrEmpty(this))).toSeq: _*
    ).asJava
  }

  @Nullable
  override def getArgumentForParameter(i: Int): UExpression = {
    val args = getValueArguments
    if (0 <= i && i < args.size()) args.get(i) else null
  }

  @Nullable
  override def resolve(): PsiMethod =
    scReference.map(_.resolveTo[PsiMethod]).orNull

  override protected def scReference: Option[ScReference] = scElement.reference

  @Nullable
  override def getExpressionType: PsiType = getReturnType
}
