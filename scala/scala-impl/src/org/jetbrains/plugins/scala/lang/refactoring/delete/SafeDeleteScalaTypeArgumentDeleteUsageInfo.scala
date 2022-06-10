package org.jetbrains.plugins.scala.lang.refactoring.delete

import com.intellij.psi.{PsiElement, PsiTypeParameter}
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceJavaDeleteUsageInfo
import org.jetbrains.plugins.scala.extensions.{IteratorExt, Parent, PsiElementExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScTypeArgs, ScTypeElement}

class SafeDeleteScalaTypeArgumentDeleteUsageInfo(element: ScTypeElement, referencedElement: PsiTypeParameter, isSafeDelete: Boolean = true)
  extends SafeDeleteReferenceJavaDeleteUsageInfo(element, referencedElement, isSafeDelete) {
  override def deleteElement(): Unit = if (isSafeDelete) {
    getElement match {
      case Parent(args: ScTypeArgs) if args.getArgsCount <= 1 =>
        args.delete()
      case element@Parent(args: ScTypeArgs) =>
        val isFirstArgument = args.typeArgs.headOption.contains(element)

        val (start, end) = if (isFirstArgument) {
          (element, lastConsecutiveWhitespaceOrComma(element.nextSiblings).getOrElse(element))
        } else {
          (lastConsecutiveWhitespaceOrComma(element.prevSiblings).getOrElse(element), element)
        }

        args.deleteChildRange(start, end)
      case _ =>
        super.deleteElement()
    }
  }

  private def lastConsecutiveWhitespaceOrComma(elements: Iterator[PsiElement]): Option[PsiElement] =
    elements
      .takeWhile(e => e.isWhitespace || e.elementType == ScalaTokenTypes.tCOMMA)
      .lastOption
}
