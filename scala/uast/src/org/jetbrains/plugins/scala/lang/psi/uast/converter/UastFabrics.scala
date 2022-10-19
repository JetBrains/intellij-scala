package org.jetbrains.plugins.scala.lang.psi.uast.converter

import com.intellij.psi.{PsiComment, PsiElement, PsiType}
import org.jetbrains.annotations.Nullable
import org.jetbrains.uast._

/**
  * Common UAST element fabrics
  */
trait UastFabrics {
  def createUEmptyExpression(@Nullable parent: UElement): UExpression =
    new UastEmptyExpression(parent)

  def createUIdentifier(@Nullable element: PsiElement,
                        @Nullable parent: UElement): UIdentifier =
    new UIdentifier(element, parent)

  def createUErrorType(): PsiType = UastErrorType.INSTANCE

  def createUComment(element: PsiComment,
                     @Nullable parent: UElement): UComment =
    new UComment(element, parent)
}
