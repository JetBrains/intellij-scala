package org.jetbrains.plugins.scala.lang.psi.implicits

import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cachedWithRecursionGuard}
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, childOf}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScMethodLike, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScExtension
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateParents
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScGiven, ScMember}
import org.jetbrains.plugins.scala.lang.psi.api.{ImplicitArgumentsOwner, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project._

case class ImplicitSearchScope(representative: PsiElement) {

  def cachedVisibleImplicits: Set[ScalaResolveResult] =
    cachedWithRecursionGuard(
      "cachedVisibleImplicits",
      representative,
      Set.empty[ScalaResolveResult],
      BlockModificationTracker(representative)
    ) {
      new ImplicitParametersProcessor(representative, withoutPrecedence = false).candidatesByPlace
    }

  def cachedImplicitsByType(scType: ScType): Set[ScalaResolveResult] =
    cachedWithRecursionGuard(
      "cachedImplicitsByType",
      representative,
      Set.empty[ScalaResolveResult],
      BlockModificationTracker(representative),
      Tuple1(scType)
    ) {
      new ImplicitParametersProcessor(representative, withoutPrecedence = true)
        .candidatesByType(scType)
    }
}

object ImplicitSearchScope {
  //should be different for two elements if they have different sets of available implicit names
  def forElement(e: PsiElement): ImplicitSearchScope = {
    e.getContainingFile match {
      case _: ScalaFile => new ImplicitSearchScope(representative(e).getOrElse(e))
      case _ => new ImplicitSearchScope(e)
    }
  }

  private def representative(e: PsiElement): Option[PsiElement] = {
    val isScala3 = e.isInScala3File

    val elements = e.withContexts
      .takeWhile(e => e != null && !e.isInstanceOf[PsiFile])
      .flatMap(_.withPrevSiblings)

    var nonBorder: Option[PsiElement] = None
    while (elements.hasNext) {
      val next = elements.next()
      if (isImplicitSearchBorder(next, isScala3))
        return nonBorder
      else if (maySearchImplicitsFor(next)) {
        nonBorder = Some(next)
      }
    }
    nonBorder
  }

  private def maySearchImplicitsFor(element: PsiElement): Boolean =
    element.isInstanceOf[ImplicitArgumentsOwner]

  private def isImplicitSearchBorder(elem: PsiElement, isScala3: Boolean): Boolean = elem match {
      //special treatment for case clauses and for comprehensions to
      //support `implicit0` from betterMonadicFor compiler plugin
      case _: ScForBinding                                                => isScala3 || elem.betterMonadicForEnabled
      case _: ScGenerator                                                 => isScala3 || elem.betterMonadicForEnabled
      case _: ScCaseClause                                                => isScala3 || elem.betterMonadicForEnabled
      case _: ScFor                                                       => isScala3 || elem.betterMonadicForEnabled
      case _: ScImportStmt | _: ScPackaging | _: ScExtension | _: ScGiven => true
      case (_: ScParameters) childOf (m: ScMethodLike)                    => hasImplicitClause(m)
      case pc: ScPrimaryConstructor                                       => hasImplicitClause(pc)
      case (ps: ScParameters) childOf (_: ScFunctionExpr)                 => ps.params.exists(_.isImplicitOrContextParameter)
      case polyFun: ScPolyFunctionExpr                                    => polyFun.typeParameters.exists(_.contextBounds.nonEmpty)
      case p: ScParameter                                                 => p.isImplicitOrContextParameter
      case m: ScMember                                                    => m.hasModifierProperty("implicit")
      case _: ScTemplateParents                                           => true
      case (expr: ScExpression) childOf (_: ScArgumentExprList | _: ScFunctionExpr)
          if isScala3 && expr.contextFunctionParameters.nonEmpty =>
        true
      case (block: ScBlockExpr) childOf (_: ScInfixExpr)
        if isScala3 && block.contextFunctionParameters.nonEmpty =>
        true
      case _ => false
    }

  private def hasImplicitClause(m: ScMethodLike): Boolean = m.effectiveParameterClauses.exists(_.isImplicitOrUsing)
}