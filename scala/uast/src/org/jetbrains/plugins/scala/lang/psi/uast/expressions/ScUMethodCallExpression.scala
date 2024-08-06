package org.jetbrains.plugins.scala.lang.psi.uast.expressions

import com.intellij.psi.{PsiElement, PsiMethod, PsiType}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeArgs
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.{ScUAnnotated, ScUExpression, ScUMultiResolvable}
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.plugins.scala.uast.ReferenceExt
import org.jetbrains.uast.{UCallExpression, UCallExpressionAdapter, UExpression, UIdentifier, UReferenceExpression, UastCallKind}

import java.{util => ju}
import scala.jdk.CollectionConverters._

trait ScUMethodCallCommon
  extends UCallExpression
    with ScUExpression
    with ScUAnnotated
    with ScUMultiResolvable {

  //region Abstract members
  protected def getReferencedExpr: Option[ScExpression]

  protected def getTypeArgs: Option[ScTypeArgs]
  //endregion

  override type PsiFacade = PsiElement

  @Nullable
  override def getClassReference: UReferenceExpression = null

  override def getKind: UastCallKind = UastCallKind.METHOD_CALL

  @Nullable
  override def getMethodIdentifier: UIdentifier =
    scReference.map(ref => createUIdentifier(ref.nameId, this)).orNull

  @Nullable
  override def getMethodName: String = scReference.map(_.refName).orNull

  @Nullable
  override def getReceiver: UExpression = {
    val receiverOpt = for {
      e <- getReferencedExpr
      qualifiedRef <- e.convertTo[UReferenceExpression](this)
    } yield qualifiedRef match {
      case q: ScUQualifiedReferenceExpression => q.getReceiver
      case r => r
    }

    receiverOpt.orNull
  }

  @Nullable
  override def getReceiverType: PsiType =
    getReferencedExpr.flatMap(_.`type`().map(_.toPsiType).toOption).orNull

  @Nullable
  override def getReturnType: PsiType =
    scExpression.`type`().map(_.toPsiType).getOrElse(null)

  override def getTypeArgumentCount: Int =
    getTypeArgs.map(_.getArgsCount).getOrElse(0)

  override def getTypeArguments: ju.List[PsiType] =
    getTypeArgs
      .map(_.typeArgs.flatMap(_.`type`().map(_.toPsiType).toOption))
      .getOrElse(Seq.empty)
      .asJava

  @Nullable
  override def getArgumentForParameter(i: Int): UExpression = {
    // TODO: not implemented properly
    val args = getValueArguments
    if (0 <= i && i < args.size()) args.get(i) else null
  }

  @Nullable
  override def resolve(): PsiMethod =
    scReference.map(_.resolveTo[PsiMethod]).orNull

  override def asLogString: String = s"UMethodCall(name = $getMethodName)"
}

/**
  * [[ScMethodCall]] adapter for the [[UCallExpression]]
  * with kind [[UastCallKind.METHOD_CALL]]
  *
  * @param scExpression Scala PSI element representing method invocation
  */
final class ScUMethodCallExpression(
  override protected val scExpression: ScMethodCall,
  override protected val parent: LazyUElement,
) extends UCallExpressionAdapter
    with ScUMethodCallCommon {

  override def getSourcePsi: PsiElement = scExpression

  override protected def getReferencedExpr: Option[ScExpression] =
    Option(scExpression.getInvokedExpr)

  override protected def getTypeArgs: Option[ScTypeArgs] =
    getReferencedExpr
      .collect { case c: ScGenericCall => c.typeArgs }

  override def getValueArgumentCount: Int = scExpression.args.exprs.size

  // TODO add conversion of CBN-parameters to implicit lambdas
  override def getValueArguments: ju.List[UExpression] =
    scExpression match {
      case blockArguments if blockArguments.args.isBlockArgs =>
        scExpression.args.exprs.collect {
          case ScBlock(statement) =>
            statement.convertToUExpressionOrEmpty(parent = this)
          case partialLambda: ScBlock if partialLambda.isPartialFunction =>
            partialLambda.convertToUExpressionOrEmpty(parent = this)
        }.asJava
      case parensArguments =>
        parensArguments.argumentExpressions
          .map(_.convertToUExpressionOrEmpty(parent = this))
          .asJava
    }

  override protected def scReference: Option[ScReference] =
    Option(scExpression.getInvokedExpr).flatMap {
      case ref: ScReference => Some(ref)
      case genericCall: ScGenericCall =>
        Option(genericCall.referencedExpr).collect {
          case ref: ScReference => ref
        }
      case _ => None
    }
}

/**
  * [[ScGenericCall]] adapter for the [[UCallExpression]]
  * with kind [[UastCallKind.METHOD_CALL]]
  *
  * @param scExpression Scala PSI element representing generic call
  */
final class ScUGenericCallExpression(
  override protected val scExpression: ScGenericCall,
  override protected val parent: LazyUElement,
) extends UCallExpressionAdapter
    with ScUMethodCallCommon {

  override def getSourcePsi: PsiElement = scExpression

  override protected def getReferencedExpr: Option[ScExpression] =
    Option(scExpression.referencedExpr)

  override protected def getTypeArgs: Option[ScTypeArgs] = Some(scExpression.typeArgs)

  override def getValueArgumentCount: Int = 0

  override def getValueArguments: ju.List[UExpression] =
    ju.Collections.emptyList()

  @Nullable
  override def getArgumentForParameter(i: Int): UExpression = null

  override protected def scReference: Option[ScReference] =
    Option(scExpression.referencedExpr).collect { case ref: ScReference => ref }
}

final class ScUPostfixExpressionCall(
  override protected val scExpression: ScPostfixExpr,
  override protected val parent: LazyUElement,
) extends UCallExpressionAdapter
  with ScUMethodCallCommon {
  override def getSourcePsi: PsiElement = scExpression

  override protected def scReference: Option[ScReference] = Some(scExpression.operation)

  override protected def getReferencedExpr: Option[ScExpression] = Some(scExpression.operation)

  override protected def getTypeArgs: Option[ScTypeArgs] = getReferencedExpr
    .collect { case c: ScGenericCall => c.typeArgs }

  override def getValueArgumentCount: Int = 0

  override def getValueArguments: ju.List[UExpression] = ju.Collections.emptyList()

  override def getReceiver: UExpression =
    scExpression.operand.convertTo[UExpression](this).orNull
}

final class ScUBinaryExpressionCall(
  override protected val scExpression: ScInfixExpr,
  override protected val parent: LazyUElement,
) extends UCallExpressionAdapter
  with ScUMethodCallCommon {
  private lazy val args: ju.List[UExpression] = (scExpression.right match {
    case tuple: ScTuple => tuple.exprs.toList
    case null | _: ScUnitExpr => Nil
    case expr => expr :: Nil
  }).flatMap(_.convertTo[UExpression](this)).asJava

  override def getSourcePsi: PsiElement = scExpression

  override protected def scReference: Option[ScReference] = Some(scExpression.operation)

  override protected def getReferencedExpr: Option[ScExpression] = Some(scExpression.operation)

  override protected def getTypeArgs: Option[ScTypeArgs] = getReferencedExpr
    .collect { case c: ScGenericCall => c.typeArgs }

  override def getValueArgumentCount: Int = args.size()

  override def getValueArguments: ju.List[UExpression] = args

  @Nullable
  override def getReceiver: UExpression = scExpression.left.convertTo[UExpression](this).orNull
}

/**
  * [[ScReferenceExpression]] adapter for the [[UCallExpression]]
  * with kind [[UastCallKind.METHOD_CALL]]
  *
  * @param scExpression Scala PSI element representing paren-less call
  *                     (e.g. `obj.toString`)
  */
final class ScUReferenceCallExpression(
  override protected val scExpression: ScReferenceExpression,
  override protected val parent: LazyUElement,
) extends UCallExpressionAdapter
    with ScUMethodCallCommon {

  override def getSourcePsi: PsiElement = scExpression

  override protected def getReferencedExpr: Option[ScExpression] =
    Some(scExpression)

  override protected def getTypeArgs: Option[ScTypeArgs] = None

  override protected def scReference: Option[ScReference] = Some(scExpression)

  override def getValueArgumentCount: Int = 0

  override def getValueArguments: ju.List[UExpression] =
    ju.Collections.emptyList()
}
