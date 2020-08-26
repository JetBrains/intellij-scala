package org.jetbrains.plugins.scala.lang.psi.uast.declarations

import java.util

import com.intellij.psi.{PsiAnnotation, PsiClass}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScConstructorInvocation, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAssignment
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.{ScUElement, ScUMultiResolvable}
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.expressions.{ScUNamedExpression, ScUUnnamedExpression}
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.plugins.scala.uast.ReferenceExt
import org.jetbrains.uast.{UAnchorOwner, UAnnotation, UAnnotationAdapter, UAnnotationEx, UCallExpression, UExpression, UIdentifier, UNamedExpression}

import scala.jdk.CollectionConverters._

/**
 * [[ScAnnotation]] adapter for the [[UAnnotation]]
 *
 * @param scElement Scala PSI element representing annotation
 */
final class ScUAnnotation(override protected val scElement: ScAnnotation,
                          override protected val parent: LazyUElement)
    extends UAnnotationAdapter
    with ScUElement
    with UAnchorOwner
    with UAnnotationEx
    with ScUMultiResolvable {
  thisAnnotation =>

  override type PsiFacade = PsiAnnotation

  @Nullable
  override def getQualifiedName: String = scElement.getQualifiedName

  override def getAttributeValues: util.List[UNamedExpression] = {
    val result: collection.Seq[UNamedExpression] = scElement.annotationExpr.getAnnotationParameters
      .map {
        case namedArg: ScAssignment =>
          new ScUNamedExpression(namedArg, LazyUElement.just(this))
        case unnamedArg =>
          new ScUUnnamedExpression(unnamedArg, LazyUElement.just(this))
      }
    result.asJava
  }

  def constructorInvocation: Option[UCallExpression] =
    scElement.constructorInvocation.convertTo[UCallExpression](this)

  @Nullable
  override def findAttributeValue(name: String): UExpression =
    Option(scElement.findAttributeValue(name))
      .map(_.convertToUExpressionOrEmpty(this))
      .orNull

  @Nullable
  override def findDeclaredAttributeValue(name: String): UExpression =
    Option(scElement.findAttributeValue(name))
      .map(_.convertToUExpressionOrEmpty(this))
      .orNull

  @Nullable
  override def resolve(): PsiClass =
    scReference.map(_.resolveTo[PsiClass]).orNull

  override protected def scReference: Option[ScReference] =
    scElement.constructorInvocation.reference

  @Nullable
  override def getUastAnchor: UIdentifier =
    scReference.map(ref => createUIdentifier(ref.nameId, this)).orNull

  def uastAnchor: Option[UIdentifier] = Option(getUastAnchor)
}

object ScUAnnotation {

  object fromConstructorInvocation {
    def unapply(arg: ScConstructorInvocation): Option[ScAnnotation] =
      Option(arg.getParent)
        .flatMap(annotationExpr => Option(annotationExpr.getParent))
        .collect { case a: ScAnnotation => a }
  }
}
