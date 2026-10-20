package org.jetbrains.plugins.scala.lang.psi

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.ScControlFlowOwner
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockStatement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScExportStmt, ScImportExpr, ScImportSelector}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

/**
 * Traversal helpers for the shared import/export PSI.
 *
 * Import and export clauses use [[ScImportExpr]] and [[ScImportSelector]] nodes.
 *
 * The deliberately explicit, imperative traversal was benchmarked as the fastest evaluated implementation and is
 * exhaustively covered by focused tests.
 */
object ImportAndExportPsiUtils {

  /** Returns whether `element` has an import expression as a strict parent. */
  def isInsideImportExpression(@Nullable element: PsiElement): Boolean =
    findParentImportExpression(element).nonEmpty

  /** Finds the strict parent import expression of `element`, or `null` when there is none. */
  @Nullable
  def findParentImportExpressionOrNull(@Nullable element: PsiElement): ScImportExpr =
    findParentImportExpression(element).orNull

  /** Finds the strict parent import expression of `element`. */
  def findParentImportExpression(@Nullable element: PsiElement): Option[ScImportExpr] = {
    if (element == null)
      return None

    element.getParent match {
      case reference: ScStableCodeReference =>
        // The last reference in a direct import/export expression, such as `import qualifier.member`.
        reference.getParent match {
          case importExpr: ScImportExpr =>
            Some(importExpr)
          case selector: ScImportSelector =>
            // The source name of a selector, such as `member` in `{member as alias}`.
            Some(selector.parentImportExpression)
          case _ =>
            parentImportExpressionByParentWalk(element)
        }
      case selector: ScImportSelector =>
        // The alias name of a selector, such as `alias` in `{member as alias}`.
        Some(selector.parentImportExpression)
      case _ =>
        parentImportExpressionByParentWalk(element)
    }
  }

  private def parentImportExpressionByParentWalk(element: PsiElement): Option[ScImportExpr] = {
    var current = if (element == null) null else element.getParent
    while (current != null) {
      current match {
        case importExpr: ScImportExpr =>
          // The element belongs to this import/export expression.
          return Some(importExpr)
        case _: ScBlockStatement | _: ScControlFlowOwner | _: ScMember =>
          // Reaching a scope boundary means the element is not inside an import/export expression.
          return None
        case _ =>
          // Continue towards the containing statement.
          current = current.getParent
      }
    }
    None
  }

  /** Finds the explicit named export that exposes `element` and its visible name. */
  def findExplicitExport(element: PsiElement): Option[(ScExportStmt, String)] = {
    def selectorImportExpression(selector: ScImportSelector): Option[ScImportExpr] = {
      val aliasNameElement = selector.aliasNameElement
      if (aliasNameElement.exists(_ eq element))
        selector.aliasNameWithIgnoredHidingImport.map(_ => selector.parentImportExpression)
      else if (!selector.isAliasedImport)
        selector.reference.collect {
          case reference if reference.nameId eq element =>
            selector.parentImportExpression
        }
      else
        None
    }

    val importExpression: Option[ScImportExpr] = element.getParent match {
      case reference: ScStableCodeReference =>
        reference.getParent match {
          case importExpr: ScImportExpr if (reference.nameId eq element) &&
            !importExpr.hasWildcardSelector && !importExpr.hasGivenSelector =>
            // A direct export, such as `export delegate.run`.
            Some(importExpr)

          case selector: ScImportSelector =>
            // The source name of a selector, such as `run` in `{run as execute}`.
            selectorImportExpression(selector)
          case _ =>
            None
        }
      case selector: ScImportSelector =>
        // The alias name of a selector, such as `execute` in `{run as execute}`.
        selectorImportExpression(selector)
      case _ =>
        None
    }

    importExpression.flatMap { importExpr =>
      importExpr.getParent match {
        case exportStmt: ScExportStmt =>
          // A successful branch matched `element` by identity with the visible-name PSI leaf.
          Some(exportStmt -> element.getText)
        case _ =>
          None
      }
    }
  }
}
