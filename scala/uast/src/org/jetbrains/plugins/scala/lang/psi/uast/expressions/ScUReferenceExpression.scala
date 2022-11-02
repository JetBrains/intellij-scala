package org.jetbrains.plugins.scala.lang.psi.uast.expressions

import com.intellij.psi.{PsiElement, PsiMethod, PsiNamedElement, PsiType}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScGenericCall, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.{ScUAnnotated, ScUElement, ScUMultiResolvable}
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.uast._

trait ScUCommonReferenceExpression
    extends UReferenceExpression
    with ScUElement
    with ScUMultiResolvable
    with ScUAnnotated {

  protected def typeProvider: Option[Typeable]

  override protected val scElement: ScReference

  override type PsiFacade = PsiElement

  @Nullable
  override def getJavaPsi: PsiElement = null

  @Nullable
  override def getResolvedName: String = resolve() match {
    case named: PsiNamedElement => named.getName
    case _                      => null
  }

  @Nullable
  override def getExpressionType: PsiType =
    typeProvider.flatMap(_.`type`().map(_.toPsiType).toOption).orNull

  override protected def scReference: Option[ScReference] = Some(scElement)

  @Nullable
  override def resolve(): PsiElement = scElement.resolve()
}

/**
  * [[ScReference]] adapter for the [[USimpleNameReferenceExpression]]
  *
  * @param scElement Scala PSI element representing simple unqualified reference
  */
final class ScUSimpleNameReferenceExpression(
  override protected val scElement: ScReference,
  override protected val typeProvider: Option[Typeable],
  override protected val parent: LazyUElement
) extends USimpleNameReferenceExpressionAdapter
    with ScUCommonReferenceExpression {

  override def getIdentifier: String = scElement.refName
}

/**
  * [[ScReference]] adapter for the [[UQualifiedReferenceExpression]]
  *
  * @param scElement    Scala PSI element representing qualified reference
  * @param typeProvider Optional type that will be represented by this instance
  */
final class ScUQualifiedReferenceExpression(
  override protected val scElement: ScReference,
  override protected val typeProvider: Option[Typeable],
  @Nullable sourcePsi: PsiElement,
  override protected val parent: LazyUElement
) extends UQualifiedReferenceExpressionAdapter
    with ScUCommonReferenceExpression {

  def this(scElement: ScReference,
           typeProvider: Option[Typeable],
           parentProvider: LazyUElement) =
    this(scElement, typeProvider, scElement, parentProvider)

  def this(scElement: ScReferenceExpression,
           @Nullable sourcePsi: PsiElement,
           parentProvider: LazyUElement) =
    this(scElement, Some(scElement), sourcePsi, parentProvider)

  def this(scElement: ScReferenceExpression, parentProvider: LazyUElement) =
    this(scElement, Some(scElement), parentProvider)

  @Nullable
  override def getSourcePsi: PsiElement = sourcePsi

  override def getAccessType: UastQualifiedExpressionAccessType =
    UastQualifiedExpressionAccessType.SIMPLE

  override def getReceiver: UExpression =
    scElement.qualifier.convertToUExpressionOrEmpty(this)

  override def getSelector: UExpression =
    scElement.getParent match {
      case scMethodCall: ScMethodCall =>
        new ScUMethodCallExpression(scMethodCall, LazyUElement.just(this))
      case scGenericCall: ScGenericCall =>
        scGenericCall.getParent match {
          case mc: ScMethodCall =>
            new ScUMethodCallExpression(mc, LazyUElement.just(this))
          case _ =>
            new ScUGenericCallExpression(scGenericCall, LazyUElement.just(this))
        }
      case _ =>
        scElement match {
          case methodRef @ ScReferenceExpression(
                _: PsiMethod | _: ScSyntheticFunction
              ) =>
            functionReferenceCall(methodRef, LazyUElement.just(this))
          case _ =>
            new ScUSimpleNameReferenceExpression(
              scElement,
              typeProvider,
              LazyUElement.just(this)
            )
        }
    }

  /**
   * For now, for some reason qualified method calls are represented by reference expression.<br>
   * It's whether a historical reason, or most likely it was just copied from Java, where ot was actual historical reason.
   * (See comments to SCL-20546)
   *
   * See related code in [[org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter]]<br>
   * In particular `case e: ScMethodCall =>`
   *
   * UAST structure could be improved: we could represent qualified method call as ScUMethodCallExpression as well.
   * But for now this quick fix is enough.
   */
  override def getExpressionType: PsiType = {
    val effectiveTypeable: Option[Typeable] = scElement.getParent match {
      case mc: ScMethodCall =>
        Some(mc)
      case gc: ScGenericCall =>
        Some(gc.getParent match {
          case mc: ScMethodCall => mc
          case _ => gc
        })
      case _ =>
        typeProvider
    }
    effectiveTypeable.flatMap(_.`type`().map(_.toPsiType).toOption).orNull
  }
}

/**
  * [[ScReference]] adapter for the [[UTypeReferenceExpression]]
  *
  * @param scElement    Scala PSI element representing reference to some type
  * @param typeProvider Type that will be represented by this instance
  */
final class ScUTypeReferenceExpression(
  override protected val scElement: ScReference,
  override protected val typeProvider: Option[Typeable],
  @Nullable sourcePsi: PsiElement,
  override protected val parent: LazyUElement
) extends UTypeReferenceExpressionAdapter
    with ScUCommonReferenceExpression
    with UReferenceExpression {

  @Nullable
  override def getSourcePsi: PsiElement = sourcePsi

  override def getType: PsiType =
    typeProvider.map(_.uastType()).getOrElse(createUErrorType())

  @Nullable
  override def getQualifiedName: String =
    typeProvider
      .flatMap(_.`type`().map(_.canonicalText).toOption)
      .orNull
}

/**
  * [[ScReference]] adapter for the [[UCallableReferenceExpression]]
  *
  * @param scElement    Scala PSI element representing java-like reference on
  *                     a method, e.g.
  *                     {{{
  *                       // --------------v
  *                       Seq().foreach(println)
  *                       // or also
  *                       Seq().foreach(println _)
  *                     }}}
  */
final class ScUCallableReferenceExpression(
  override protected val scElement: ScReference,
  override protected val parent: LazyUElement
) extends UCallableReferenceExpressionAdapter
    with ScUCommonReferenceExpression {

  override protected def typeProvider: Option[Typeable] = None

  override def getCallableName: String = scElement.refName

  @Nullable
  override def getQualifierExpression: UExpression = null

  @Nullable
  override def getQualifierType: PsiType =
    scElement
      .multiResolveScala(incomplete = false)
      .collectFirst {
        case pm: PsiMethod =>
          Option(pm.getContainingClass)
            .map(
              cls =>
                PsiType.getTypeByName(
                  cls.getQualifiedName,
                  scElement.getProject,
                  scElement.getResolveScope
              )
            )
            .orNull
      }
      .orNull
}

object ScUReferenceExpression {
  def unapply(ref: ScReference): Option[Parent2ScUReferenceExpression] = {
    val typeProvider: Option[Typeable] = Option(ref).collect {
      case it: ScReferenceExpression => it
    }

    Some(
      if (ref.qualifier.isDefined)
        new ScUQualifiedReferenceExpression(ref, typeProvider, _)
      else
        new ScUSimpleNameReferenceExpression(ref, typeProvider, _)
    )
  }

  trait Parent2ScUReferenceExpression {
    def apply(parent: LazyUElement): ScUCommonReferenceExpression
  }
}
