package org.jetbrains.plugins.scala.lang.completion.filters.expression

import com.intellij.psi._
import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.{NonNls, Nullable}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, Parent, PsiFileExt, &}
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/**
 * In Scala 3 there is no indicator when the enumerators end (like with braces and parentheses in Scala2).
 *
 * Example: for x <- Some(3) /* another enumerator or do/yield? */
 */
class DoYieldFilterInScala3 extends ElementFilter {
  private def leafText(i: Int, context: PsiElement): String = {
    val elem = ScalaCompletionUtil.getLeafByOffset(i, context)
    if (elem == null) return ""
    elem.getText
  }

  override def isAcceptable(element: Object, @Nullable context: PsiElement): Boolean = {
    if (context == null || context.is[PsiComment]) return false
    val leaf = PsiTreeUtil.getDeepestFirst(context)

    val parent = leaf.getParent
    val forElement = parent match {
      case (_: ScReferencePattern) & Parent((_: ScForBinding) & Parent(enum: ScEnumerators)) =>
        Some(enum.getParent)
      case (_: ScExpression) & Parent(`for`: ScFor) =>
        Some(`for`)
      case _ =>
        None
    }

    forElement match {
      case None => false
      case Some(forElement) =>
        val file = context.getContainingFile
        val fileText = file.charSequence
        var i = context.getTextRange.getStartOffset - 1
        while (i > 0 && (fileText.charAt(i) == ' ' || fileText.charAt(i) == '\n')) {
          i = i - 1
        }
        if (leafText(i, context) == "yield") return false
        i = context.getTextRange.getEndOffset
        while (i < fileText.length - 1 && (fileText.charAt(i) == ' ' || fileText.charAt(i) == '\n')) {
          i = i + 1
        }
        if (leafText(i, context) == "yield") return false
        for (child <- forElement.getNode.getChildren(null) if child.getElementType == ScalaTokenTypes.kYIELD) {
          return false
        }
        ScalaCompletionUtil.checkAnyWith(forElement, "yield true", context.getManager) ||
          ScalaCompletionUtil.checkReplace(forElement, "yield")
    }
  }

  override def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = {
    true
  }

  @NonNls
  override def toString: String = {
    "'yield' keyword filter"
  }
}