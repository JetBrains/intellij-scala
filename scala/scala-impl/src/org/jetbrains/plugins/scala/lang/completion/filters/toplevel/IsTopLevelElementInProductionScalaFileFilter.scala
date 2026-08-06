package org.jetbrains.plugins.scala.lang.completion.filters.toplevel

import com.intellij.psi.PsiElement
import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.impl.source.tree.{LeafElement, LeafPsiElement}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging

object IsTopLevelElementInProductionScalaFileFilter extends ElementFilter {

  override def toString: String = "IsTopLevelElementInProductionScalaFileFilter"

  override def isClassAcceptable(hintClass: Class[_]): Boolean = true

  override def isAcceptable(element: Object, @Nullable context: PsiElement): Boolean =
    context match {
      case leaf: LeafElement =>
        leaf.getParent match {
          case ref: ScReference =>
            IsTopLevelElementInProductionScalaFileFilter.check(ref)
          case _ => false
        }
      case _ => false
    }

  /**
   * @param ref reference wrapping synthetic idintifier used in completion<br>
   *            see [[com.intellij.codeInsight.completion.CompletionUtilCore#DUMMY_IDENTIFIER]]
   */
  def check(ref: ScReference): Boolean = {
    //At top level we don't want to be able to show any expression in completion list (unless it's a worksheet)
    //Though, if someone explicitly typed some expression at top level and wants to complete some method on the expression
    //we allow doing so
    ref.getFirstChild match {
      case _: LeafPsiElement =>
        ref.getParent match {
          case f: ScalaFile =>
            !f.isWorksheetFile
          case _: ScPackaging =>
            true
          case _ =>
            false
        }
      case _ =>
        //It's method call:
        //expr.foo<CARET>
        //"literal".foo<CARET<
        false
    }
  }

  def check(leaf: LeafPsiElement): Boolean =
    leaf.getParent match {
      case ref: ScReference => check(ref)
      case _ => false
    }
}
