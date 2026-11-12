package org.jetbrains.plugins.scala.lang.psi.impl.base

import com.intellij.psi.{PsiComment, PsiElement, PsiErrorElement, PsiWhiteSpace}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.cc.ScCaptureSet
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeBoundsOwner

/**
 * AST-backed bound accessors shared by parameters and aliases. Separate from
 * [[ScTypeBoundsOwnerImpl]] so aliases retain their own lower/upper bound semantics.
 */
trait ScTypeBoundsOwnerSyntax extends ScTypeBoundsOwner {
  override def upperTypeElement: Option[ScTypeElement] =
    boundElement(ScalaTokenTypes.tUPPER_BOUND).collect { case t: ScTypeElement => t }

  override def lowerTypeElement: Option[ScTypeElement] =
    boundElement(ScalaTokenTypes.tLOWER_BOUND).collect { case t: ScTypeElement => t }

  override def upperCaptureSet: Option[ScCaptureSet] =
    boundElement(ScalaTokenTypes.tUPPER_BOUND).collect { case captures: ScCaptureSet => captures }

  override def lowerCaptureSet: Option[ScCaptureSet] =
    boundElement(ScalaTokenTypes.tLOWER_BOUND).collect { case captures: ScCaptureSet => captures }

  // Inspect only the next syntactic element: searching for a later ScTypeElement
  // could mistake another bound or an alias RHS for a capture-set (or missing) bound.
  private def boundElement(token: IElementType): Option[PsiElement] =
    findLastChildByTypeScala[PsiElement](token).flatMap(_.nextSiblings.find(isBoundSyntaxCandidate))

  private def isBoundSyntaxCandidate(element: PsiElement): Boolean = element match {
    case _: PsiWhiteSpace | _: PsiComment | _: PsiErrorElement => false
    case _ => true
  }
}
