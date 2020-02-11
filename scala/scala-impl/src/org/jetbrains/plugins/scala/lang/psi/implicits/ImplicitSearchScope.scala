package org.jetbrains.plugins.scala.lang.psi.implicits

import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, childOf}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScMethodLike, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScForBinding, ScFunctionExpr, ScGenerator}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateParents
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.project._

/**
  * @author Nikolay.Tropin
  */
trait ImplicitSearchScope

object ImplicitSearchScope {

  private case class ImplicitSearchScopeImpl(file: PsiFile, upperBorder: Option[PsiElement]) extends ImplicitSearchScope

  //should be different for two elements if they have different sets of available implicit names
  def forElement(e: PsiElement): ImplicitSearchScope = {
    e.getContainingFile match {
      case scalaFile: ScalaFile =>
        ImplicitSearchScopeImpl(scalaFile, findBorderUp(e))
      case file => ImplicitSearchScopeImpl(file, None)
    }
  }

  private def findBorderUp(e: PsiElement): Option[PsiElement] = {
    e.withContexts
      .takeWhile(e => e != null && !e.isInstanceOf[PsiFile])
      .flatMap(_.prevSiblings)
      .find(isImplicitSearchBorder)
  }

  private def isImplicitSearchBorder(elem: PsiElement): Boolean = elem match {
    //special treatment for case clauses and for comprehensions to
    //support `implicit0` from betterMonadicFor compiler plugin
    case _: ScForBinding if elem.betterMonadicForEnabled   => true
    case _: ScGenerator  if elem.betterMonadicForEnabled   => true
    case _: ScCaseClause if elem.betterMonadicForEnabled   => true
    case _: ScImportStmt | _: ScPackaging                  => true
    case (_: ScParameters) childOf (m: ScMethodLike)       => hasImplicitClause(m)
    case pc: ScPrimaryConstructor                          => hasImplicitClause(pc)
    case (ps: ScParameters) childOf (_: ScFunctionExpr)    => ps.params.exists(_.isImplicitParameter)
    case p: ScParameter                                    => p.isImplicitParameter
    case m: ScMember                                       => m.hasModifierProperty("implicit")
    case _: ScTemplateParents                              => true
    case _                                                 => false
  }

  private def hasImplicitClause(m: ScMethodLike): Boolean = m.effectiveParameterClauses.exists(_.isImplicit)
}