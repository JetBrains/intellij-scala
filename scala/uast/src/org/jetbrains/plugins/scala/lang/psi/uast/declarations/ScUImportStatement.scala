package org.jetbrains.plugins.scala.lang.psi.uast.declarations

import java.{lang, util}

import com.intellij.psi.{PsiElement, ResolveResult}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.ScUElement
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.plugins.scala.lang.psi.uast.kinds.ScalaSpecialExpressionKinds
import org.jetbrains.plugins.scala.uast.ReferenceExt
import org.jetbrains.uast._

import scala.jdk.CollectionConverters._

/**
  * [[ScImportStmt]] adapter for the [[UImportStatement]]
  *
  * @param scElement Scala PSI element representing import statement
  */
final class ScUImportStatement(override protected val scElement: ScImportStmt,
                               override protected val parent: LazyUElement)
    extends UImportStatementAdapter
    with ScUElement
    with UMultiResolvable {

  import ScUImportStatement._

  override type PsiFacade = PsiElement

  private def allImportingReferences: Seq[ScStableCodeReference] =
    scElement.importExprs.flatMap { importExpr =>
      if (importExpr.selectors.isEmpty)
        importExpr.reference
      else
        importExpr.selectors.flatMap(_.reference)
    }

  private def singleImport: Option[ScStableCodeReference] =
    for {
      importExpr <- single(scElement.importExprs)
      singleRef <- importExpr.reference
        .filter(_ => importExpr.selectors.forall(_.isWildcardSelector)) // a.b
        .orElse(single(importExpr.selectors).flatMap(_.reference)) // a.{b}
    } yield singleRef

  /**
    * @see [[ScUImportsList]]
    */
  @Nullable
  override def getImportReference: UElement =
    singleImport
      .map(_.convertTo[UReferenceExpression](this).orNull)
      .getOrElse(
        new ScUImportsList(
          allImportingReferences
            .flatMap(_.convertTo[UReferenceExpression](this)),
          scElement,
          LazyUElement.just(this)
        )
      )

  override def isOnDemand: Boolean =
    single(scElement.importExprs).exists(_.hasWildcardSelector)

  override def multiResolve(): lang.Iterable[ResolveResult] =
    (allImportingReferences
      .flatMap(_.multiResolveScala(false)): Seq[ResolveResult]).asJava

  @Nullable
  override def resolve(): PsiElement =
    singleImport.map(_.resolveTo[PsiElement]).orNull
}

object ScUImportStatement {
  private def single[T](seq: Seq[T]): Option[T] = seq match {
    case Seq(single) => Some(single)
    case _           => None
  }
}

/**
  * Mock implementation of the [[UExpressionList]] for imports lists.
  *
  * For instance, suppose `uElement` represents next import expression:
  * {{{
  *   import a.b, a.{c, d}
  * }}}
  * Then `uElement.getImportReference` will return
  * `
  *   UExpressionList
  *     |- UReferenceExpression (a.b)
  *     |- UReferenceExpression (a.c)
  *     |- UReferenceExpression (a.d)
  * `
  *
  * @param uExprList Sequence of [[UReferenceExpression]]
  *                  which represents importing elements
  * @param scElement Source psi anchor representing given `uExprList`
  */
class ScUImportsList(uExprList: Seq[UReferenceExpression],
                     override protected val scElement: PsiElement,
                     override protected val parent: LazyUElement)
    extends UExpressionListAdapter
    with ScUElement {

  override type PsiFacade = PsiElement

  @Nullable
  override def getJavaPsi: PsiElement = null

  override def getExpressions: util.List[UExpression] =
    (uExprList: Seq[UExpression]).asJava

  override def getKind: UastSpecialExpressionKind =
    ScalaSpecialExpressionKinds.ImportsList

  override def getUAnnotations: util.List[UAnnotation] =
    util.Collections.emptyList()
}
