package org.jetbrains.plugins.scala.lang.completion.filters.other

import com.intellij.psi._
import com.intellij.psi.filters.ElementFilter
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.completion.filters.other.DerivesFilter._
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

class DerivesFilter extends ElementFilter {
  override def isAcceptable(element: Any, context: PsiElement): Boolean = {
    if (!context.isInScala3File || context.is[PsiComment]) return false
    val (leaf, _) = processPsiLeafForFilter(getLeafByOffset(context.getTextRange.getStartOffset, context))
    if (leaf == null) return false

    val errorBeforeDerivesStart = leaf.prevLeafs.filterNot(_.is[PsiComment, PsiWhiteSpace]).nextOption()

    errorBeforeDerivesStart match {
      case Some((_: PsiErrorElement) && PrevSibling(typeDefBeforeError: ScTypeDefinition)) =>
        if (typeDefBeforeError.extendsBlock.derivesClause.isDefined) false
        else {
          // Do not suggest `derives` before `extends` or another `derives`
          !leaf.nextVisibleLeaf.exists(l => isExtendsKeyword(l) || isDerivesSoftKeyword(l))
        }
      case _ => false
    }
  }

  override def isClassAcceptable(hintClass: Class[_]): Boolean = true

  @NonNls
  override def toString: String = "derives keyword filter"
}

object DerivesFilter {
  private def isExtendsKeyword(leaf: PsiElement): Boolean = leaf.elementType == ScalaTokenTypes.kEXTENDS

  private def isDerivesSoftKeyword(leaf: PsiElement): Boolean =
    leaf.elementType == ScalaTokenType.DerivesKeyword ||
      // Scala 3 Soft Keywords can be parsed as identifiers
      leaf.elementType == ScalaTokenTypes.tIDENTIFIER && leaf.textMatches(ScalaTokenType.DerivesKeyword.keywordText)
}
